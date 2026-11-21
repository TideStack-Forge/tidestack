package com.ouroboros.rpc.security;

import com.ouroboros.rpc.RPCRequest;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

/**
 * @author liansz
 **/
public interface RPCAuthCredentialBuilder<T extends RPCRequest> {

  record CredentialType(String name, String title) { }

  /** Installed builders describe their editable type; schema remains in the owning module. */
  default java.util.Optional<CredentialType> credentialType() { return java.util.Optional.empty(); }

  default io.vavr.control.Try<Void> validate(RPCCredentialMetadata metadata) { return io.vavr.control.Try.success(null); }

  /**
   * 是否支持
   *
   * @param metadata
   * @return
   */
  boolean support(RPCCredentialMetadata metadata);

  /**
   * 构建
   *
   * @param metadata
   * @return
   */
  RPCAuthCredential<T> build(RPCCredentialMetadata metadata);

  default RPCAuthCredential<T> build(RPCCredentialMetadata metadata,
                                     com.ouroboros.rpc.execution.RpcExecutionResources resources) {
    throw new UnsupportedOperationException("Credential builder does not support invocation resources");
  }
}
