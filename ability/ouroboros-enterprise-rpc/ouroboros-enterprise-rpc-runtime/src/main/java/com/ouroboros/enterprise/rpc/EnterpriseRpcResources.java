package com.ouroboros.enterprise.rpc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.token.EnterpriseApplicationTokens;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.rpc.execution.*;
import com.ouroboros.rpc.security.RPCAuthCredential;
import com.ouroboros.rpc.security.RPCAuthCredentialCenter;

/** One trusted target per channel for an invocation tree. No target is taken from input parameters. */
final class EnterpriseRpcResources implements RpcExecutionResources {
  private final RpcExecutionResources parent;
  private final EnterpriseConnectionCatalog connections;
  private final com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry providers;
  private final Map<String, EnterpriseApplicationTokens> tokens;
  private final Consumer<String> authorize;
  private final Function<String, String> configuration;
  private final java.util.function.BiFunction<RpcInvocationRequest, RpcExecutionResources, RpcInvocationResult> invoke;
  private final Map<String, Target> targets = new HashMap<>();
  private final RpcSensitiveValues secrets = new RpcSensitiveValues();
  private final long deadline;
  private final com.ouroboros.enterprise.EnterpriseApiBudget apiBudget;

  EnterpriseRpcResources(RpcExecutionResources parent, EnterpriseConnectionCatalog connections,
      com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry providers,
      Map<String, EnterpriseApplicationTokens> tokens, Consumer<String> authorize,
      Function<String, String> configuration,
      java.util.function.BiFunction<RpcInvocationRequest, RpcExecutionResources, RpcInvocationResult> invoke, long budgetMs,
      com.ouroboros.enterprise.EnterpriseApiBudget apiBudget) {
    this.parent = parent; this.connections = connections; this.providers = providers; this.tokens = Map.copyOf(tokens);
    this.authorize = authorize; this.configuration = configuration; this.invoke = invoke;
    this.apiBudget = apiBudget;
    this.deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(budgetMs);
  }

  EnterpriseApplicationTokens tokens(String provider) {
    var service = tokens.get(provider);
    if (service == null) { throw new ValidationException("企业应用令牌插件未安装"); }
    return service;
  }

  void pin(ResolvedEnterpriseConnection connection) {
    targets.put(connection.provider(), new Target(connection.connectionId(), identity(connection), connection.credential().applicationIdentity()));
    verifyCurrent(connection);
  }

  ResolvedEnterpriseConnection connection(String provider) {
    authorize.accept(provider);
    Target target = targets.get(provider);
    if (target == null) {
      List<EnterpriseConnectionCatalog.ConnectionSummary> matches = connections.listSummaries().stream()
          .filter(item -> provider.equals(item.provider())).toList();
      if (matches.size() != 1) { throw new ValidationException("该渠道必须配置唯一企业连接"); }
      var connection = resolve(matches.getFirst().connectionId(), provider);
      target = new Target(connection.connectionId(), identity(connection), connection.credential().applicationIdentity());
      targets.put(provider, target);
    }
    var current = resolve(target.connectionId(), provider);
    if (!target.configuration().equals(identity(current))
        || !target.application().equals(current.credential().applicationIdentity())) {
      throw new ValidationException("调用期间企业或应用身份发生变更，请重新发起调用");
    }
    return current;
  }

  void verifyCurrent(ResolvedEnterpriseConnection observed) {
    var current = connection(observed.provider());
    if (!java.util.Objects.equals(current.revision(), observed.revision())) {
      throw new ValidationException("调用期间连接凭据发生变更，请重新发起调用");
    }
  }

  EnterpriseDeadline tokenDeadline() {
    long remaining = remainingDurationMs();
    if (remaining <= 0) { throw new ValidationException("企业 RPC 调用预算已耗尽"); }
    return EnterpriseDeadline.afterMillis(remaining);
  }

  private ResolvedEnterpriseConnection resolve(String id, String provider) {
    return connections.resolve(id, provider).orElseThrow(() -> new ValidationException("企业连接已移除"));
  }

  private Map<String, Object> identity(ResolvedEnterpriseConnection connection) {
    var provider = providers.schemaProviderOf(connection.provider())
        .orElseThrow(() -> new ValidationException("企业连接插件未安装"));
    var identity = new java.util.LinkedHashMap<String, Object>();
    for (var key : provider.connectionIdentityFields()) {
      if (connection.config().get(key) != null) { identity.put(key, connection.config().get(key)); }
    }
    return Map.copyOf(identity);
  }

  private record Target(String connectionId, Map<String, Object> configuration, Map<String, String> application) { }

  @Override public Optional<RPCModelDefinition> findDefinition(String name) {
    return parent == null ? RPCModelDefinitionCenter.getDefinition(name) : parent.findDefinition(name);
  }
  @Override public Optional<RPCAuthCredential<?>> findCredential(String name) {
    return parent == null ? RPCAuthCredentialCenter.getRpcCredential(name).map(value -> value) : parent.findCredential(name);
  }
  @Override public String getConfig(String name) { return parent == null ? configuration.apply(name) : parent.getConfig(name); }
  @Override public Mode mode() { return parent == null ? Mode.RUNTIME : parent.mode(); }
  @Override public RpcInvocationResult invoke(RpcInvocationRequest request) { return invoke.apply(request, this); }
  @Override public long remainingDurationMs() {
    long remaining = Math.max(0, (deadline - System.nanoTime()) / 1_000_000L);
    return parent == null ? remaining : Math.min(remaining, parent.remainingDurationMs());
  }
  @Override public int maxRequestBytes() { return parent == null ? Integer.MAX_VALUE : parent.maxRequestBytes(); }
  @Override public void onExchange(RpcHttpExchange exchange) { if (parent != null) { parent.onExchange(exchange); } }
  @Override public void beforeExchange(java.net.URI target) {
    if (parent != null) { parent.beforeExchange(target); }
    for (var provider : List.copyOf(targets.keySet())) {
      var connection = connection(provider);
      if (tokens(provider).targets(connection).stream().anyMatch(contract -> contract.accepts(target))) {
        apiBudget.acquire(provider);
        return;
      }
    }
  }
  @Override public void onSecret(String value) { secrets.add(value); if (parent != null) { parent.onSecret(value); } }
  @Override public Object redact(Object value) { return secrets.redact(parent == null ? value : parent.redact(value)); }
}
