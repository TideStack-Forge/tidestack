package com.ouroboros.enterprise.rpc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.enterprise.token.EnterpriseApplicationTokens;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.rpc.execution.*;
import com.ouroboros.util.Json;

/** In-process domain adapter: the owning domain authorizes the operation before opening a session.
 * HTTP, scripts and logical-flow callers use the executor's separate credential-use authorization.
 */
public final class EnterpriseRpcClient {
  private final RpcInvocationExecutor executor;
  private final EnterpriseConnectionCatalog connections;
  private final EnterpriseConnectionProviderRegistry providers;
  private final Map<String, EnterpriseApplicationTokens> tokens;
  private final Function<String, String> configuration;
  private final com.ouroboros.enterprise.EnterpriseApiBudget apiBudget;

  public EnterpriseRpcClient(RpcInvocationExecutor executor, EnterpriseConnectionCatalog connections,
      EnterpriseConnectionProviderRegistry providers, List<EnterpriseApplicationTokens> tokens,
      Function<String, String> configuration, com.ouroboros.enterprise.EnterpriseApiBudget apiBudget) {
    this.executor = executor; this.connections = connections; this.providers = providers;
    this.tokens = tokens.stream().collect(Collectors.toUnmodifiableMap(EnterpriseApplicationTokens::provider, Function.identity()));
    this.configuration = configuration;
    this.apiBudget = apiBudget;
  }

  public Session session(ResolvedEnterpriseConnection connection, EnterpriseDeadline deadline) throws Exception {
    var resources = new EnterpriseRpcResources(null, connections, providers, tokens, provider -> {
      if (!connection.provider().equals(provider)) { throw new BusinessException("领域调用不能切换企业渠道"); }
    }, configuration, executor::invoke, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadline.remainingNanos())), apiBudget);
    resources.pin(connection);
    return new Session(resources);
  }

  public final class Session {
    private final EnterpriseRpcResources resources;
    private Session(EnterpriseRpcResources resources) { this.resources = resources; }

    public JsonNode invoke(String model, Map<String, Object> parameters) {
      var result = executor.invoke(new RpcInvocationRequest(model, parameters), resources);
      if (result.isFailure()) { throw new BusinessException("企业 API 调用失败: " + model, result.getError()); }
      return Json.mapperCopy().valueToTree(result.getPayload());
    }
  }
}
