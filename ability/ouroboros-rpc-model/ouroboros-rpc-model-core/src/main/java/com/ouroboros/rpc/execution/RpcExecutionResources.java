package com.ouroboros.rpc.execution;

import java.util.Optional;

import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.security.RPCAuthCredential;

/** Explicit dependencies for one invocation tree; never installed in a global center. */
public interface RpcExecutionResources {
  enum Mode { RUNTIME, DEVELOPMENT_TEST }

  Optional<RPCModelDefinition> findDefinition(String modelName);

  Optional<RPCAuthCredential<?>> findCredential(String credentialName);

  String getConfig(String name);

  Mode mode();

  default RpcInvocationResult invoke(RpcInvocationRequest request) {
    throw new UnsupportedOperationException("Nested invocation is unavailable");
  }

  default long remainingDurationMs() { return Long.MAX_VALUE; }

  default int maxRequestBytes() { return Integer.MAX_VALUE; }

  default void onExchange(RpcHttpExchange exchange) { }

  /** Called for each actual HTTP attempt, including permitted retries, before transport admission. */
  default void beforeExchange(java.net.URI target) { }

  /** Secrets acquired outside a token-fetch RPC must reach the existing evidence boundary too. */
  default void onSecret(String value) { }

  default Object redact(Object value) { return value; }
}
