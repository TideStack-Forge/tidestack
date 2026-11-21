package com.ouroboros.rpc.security;

import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.rpc.exception.AuthenticateException;
import com.ouroboros.rpc.security.metadata.RPCCredentialMetadata;

public class StaticAccessTokenRPCAuthCredential extends AbstractAccessTokenRPCAuthCredential {
  private final String accessToken;
  private final String accessTokenConfigKey;
  private final boolean accessTokenFromConfig;
  private final Function<String, String> configGetter;

  public StaticAccessTokenRPCAuthCredential(RPCCredentialMetadata metadata, Function<String, String> configGetter) {
    super(metadata, configGetter);
    this.configGetter = configGetter;
    String accessToken = getExtraPropDeep(String.class, "accessToken", "staticAccessToken").orElse("").trim();
    if (accessToken.matches("#\\{[^{}]*}")) {
      this.accessTokenConfigKey = accessToken.substring(2, accessToken.length() - 1);
      this.accessToken = "";
      this.accessTokenFromConfig = true;
    } else {
      this.accessToken = accessToken;
      this.accessTokenConfigKey = "";
      this.accessTokenFromConfig = false;
    }
  }

  @Override
  protected String getAccessToken() {
    if (!accessTokenFromConfig) {
      if (StringUtils.isBlank(accessToken)) {
        throw new AuthenticateException("认证凭据[" + getName() + "]静态访问令牌为空");
      }
      try { return com.ouroboros.rpc.util.RpcConfigurationTemplates.resolve(accessToken, configGetter); }
      catch (RuntimeException exception) { throw new AuthenticateException("认证凭据[" + getName() + "]静态访问令牌配置解析失败", exception); }
    }
    if (StringUtils.isBlank(accessTokenConfigKey)) {
      throw new AuthenticateException("认证凭据[" + getName() + "]静态访问令牌配置键为空或无效");
    }
    String resolvedAccessToken = configGetter.apply(accessTokenConfigKey);
    if (StringUtils.isBlank(resolvedAccessToken)) {
      throw new AuthenticateException("认证凭据[" + getName() + "]配置键[" + accessTokenConfigKey + "]对应的静态访问令牌为空");
    }
    return resolvedAccessToken;
  }
}
