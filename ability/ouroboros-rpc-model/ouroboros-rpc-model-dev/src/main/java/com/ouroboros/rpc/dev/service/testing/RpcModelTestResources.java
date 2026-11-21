package com.ouroboros.rpc.dev.service.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.rpc.execution.RpcExecutionResources;
import com.ouroboros.rpc.execution.RpcHttpExchange;
import com.ouroboros.rpc.execution.RpcInvocationService;
import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.security.RPCAuthCredential;
import com.ouroboros.rpc.security.RPCAuthCredentialCenter;

/** One snapshot, one config view and one credential/cache lifecycle per click. */
final class RpcModelTestResources implements RpcExecutionResources {
  private final RpcInvocationService executor;
  private final Function<String, String> configuration;
  private final Map<String, RPCModelDefinition> definitions = new HashMap<>();
  private final Map<String, RPCAuthCredential<?>> credentials = new HashMap<>();
  private final List<RpcHttpExchange> exchanges = new ArrayList<>();
  private final long deadline;
  private final int requestLimit;
  private final RpcModelTestRedactor redactor;

  RpcModelTestResources(RpcModelTestSnapshot snapshot, RpcInvocationService executor,
                        Function<String, String> configuration,
                        RpcModelTestRedactor redactor, long deadline, int requestLimit) {
    this.executor = executor; this.configuration = configuration;
    this.deadline = deadline; this.requestLimit = requestLimit;
    this.redactor = redactor;
    for (var name : snapshot.modelContents().keySet()) {
      var metadata = snapshot.model(name);
      if (!metadata.isEnabled()) {
        throw new RpcModelTestFailure("RESOURCE", "RESOURCE_UNAVAILABLE", "RPC 模型已禁用", name, null);
      }
      definitions.put(name, RPCModelDefinitionCenter.build(metadata).getOrElseThrow(error ->
          new RpcModelTestFailure("RESOURCE", "RESOURCE_UNAVAILABLE", "RPC 模型类型不支持或定义无效", name, null)));
    }
    for (var name : snapshot.credentialContents().keySet()) {
      var metadata = snapshot.credential(name);
      redactor.credential(metadata.getExtraProps());
      credentials.put(name, RPCAuthCredentialCenter.build(metadata, this).getOrElseThrow(error ->
          new RpcModelTestFailure("AUTH", "AUTHENTICATION_FAILED", "认证类型不支持调用级资源或定义无效", name, null)));
    }
  }

  public Optional<RPCModelDefinition> findDefinition(String name) { return Optional.ofNullable(definitions.get(name)); }
  public Optional<RPCAuthCredential<?>> findCredential(String name) { return Optional.ofNullable(credentials.get(name)); }
  public Mode mode() { return Mode.DEVELOPMENT_TEST; }
  public long remainingDurationMs() { return Math.max(0L, (deadline - System.nanoTime()) / 1_000_000L); }
  public int maxRequestBytes() { return requestLimit; }

  public String getConfig(String name) {
    return configuration.apply(name);
  }

  public RpcInvocationResult invoke(RpcInvocationRequest request) { return executor.invoke(request, this); }
  public void onExchange(RpcHttpExchange exchange) { exchanges.add(exchange); }
  public void onSecret(String value) { redactor.addSecret(value); }
  List<RpcHttpExchange> exchanges() { return List.copyOf(exchanges); }
}
