package com.ouroboros.rpc.security;

import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.rpc.execution.RpcInvocationExecutor;
import com.ouroboros.rpc.http.HttpRPCRequest;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

/**
 * @author liansz
 **/
@Component
public class AccessTokenRpcAuthCredential {
  private static ConfigurationService configurationService;
  private static Environment environment;
  private static RpcInvocationExecutor rpcInvocationExecutor;

  public AccessTokenRpcAuthCredential(
      ConfigurationService configurationService,
      Environment environment,
      ObjectProvider<RpcInvocationExecutor> rpcInvocationExecutorProvider
  ) {
    AccessTokenRpcAuthCredential.configurationService = configurationService;
    AccessTokenRpcAuthCredential.environment = environment;
    AccessTokenRpcAuthCredential.rpcInvocationExecutor = rpcInvocationExecutorProvider.getIfAvailable();
  }

  private static String getConfig(String key) {
    return configurationService.get(key)
        .map(Object::toString)
        .orElseGet(() -> environment.getProperty(key));
  }

  static RpcInvocationExecutor getRpcInvocationExecutor() {
    return rpcInvocationExecutor;
  }

  static void setRpcInvocationExecutor(RpcInvocationExecutor rpcInvocationExecutor) {
    AccessTokenRpcAuthCredential.rpcInvocationExecutor = rpcInvocationExecutor;
  }

  public static class Builder implements RPCAuthCredentialBuilder<HttpRPCRequest> {
    @Override public java.util.Optional<CredentialType> credentialType() {
      return java.util.Optional.of(new CredentialType("AccessToken", "访问令牌"));
    }
    @Override
    public boolean support(RPCCredentialMetadata metadata) {
      return metadata != null && "AccessToken".equalsIgnoreCase(metadata.getType());
    }

    @Override
    public RPCAuthCredential<HttpRPCRequest> build(RPCCredentialMetadata metadata) {
      metadata = com.ouroboros.rpc.util.RpcConfigurationTemplates.credential(metadata, AccessTokenRpcAuthCredential::getConfig);
      String mode = metadata.getExtraPropDeep(String.class, "accessToken", "mode")
          .orElse("dynamic");
      if ("static".equalsIgnoreCase(mode)) {
        return new StaticAccessTokenRPCAuthCredential(metadata, AccessTokenRpcAuthCredential::getConfig);
      }
      return new DynamicAccessTokenRPCAuthCredential(metadata, AccessTokenRpcAuthCredential::getConfig);
    }

    @Override
    public RPCAuthCredential<HttpRPCRequest> build(RPCCredentialMetadata metadata,
        com.ouroboros.rpc.execution.RpcExecutionResources resources) {
      metadata = com.ouroboros.rpc.util.RpcConfigurationTemplates.credential(metadata, resources::getConfig);
      var mode = metadata.getExtraPropDeep(String.class, "accessToken", "mode").orElse("dynamic");
      if ("static".equalsIgnoreCase(mode)) {
        return new StaticAccessTokenRPCAuthCredential(metadata, resources::getConfig);
      }
      return new DynamicAccessTokenRPCAuthCredential(metadata, resources::getConfig,
          resources::invoke, new com.ouroboros.cache.MemoryCacheManager());
    }
  }
}
