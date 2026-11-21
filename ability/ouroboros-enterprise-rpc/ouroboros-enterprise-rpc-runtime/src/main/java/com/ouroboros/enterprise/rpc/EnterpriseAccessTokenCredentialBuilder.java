package com.ouroboros.enterprise.rpc;

import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.RPCAuthCredential;
import com.ouroboros.rpc.security.RPCAuthCredentialBuilder;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

/** SPI discovery constructs definitions only; token access starts at invocation. */
public final class EnterpriseAccessTokenCredentialBuilder implements RPCAuthCredentialBuilder<HttpRPCRequest> {
  @Override public java.util.Optional<CredentialType> credentialType() {
    return java.util.Optional.of(new CredentialType("EnterpriseAccessToken", "企业应用令牌"));
  }
  @Override public boolean support(RPCCredentialMetadata metadata) {
    return metadata != null && "EnterpriseAccessToken".equalsIgnoreCase(metadata.getType());
  }
  @Override public io.vavr.control.Try<Void> validate(RPCCredentialMetadata metadata) {
    return io.vavr.control.Try.of(() -> { build(metadata); return null; });
  }
  @Override public RPCAuthCredential<HttpRPCRequest> build(RPCCredentialMetadata metadata) {
    return new EnterpriseAccessTokenCredential(metadata);
  }
  @Override public RPCAuthCredential<HttpRPCRequest> build(RPCCredentialMetadata metadata,
      com.ouroboros.rpc.execution.RpcExecutionResources resources) { return build(metadata); }
}
