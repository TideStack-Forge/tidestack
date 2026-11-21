package com.ouroboros.enterprise.auth;

import java.util.Map;

/**
 * Non-sensitive, published connection facts available to a provider auth facet.
 *
 * <p>The credential reference is intentionally retained as a reference only. An auth facet may
 * resolve and parse it through the security secret resolver, but it must never return or persist
 * the credential payload in this request.</p>
 */
public record EnterpriseConnectionAuthProfileRequest(
    String connectionId,
    String provider,
    String revision,
    String credentialRef,
    Map<String, Object> providerConfig,
    boolean authEnabled) {

  /** 兼容未携带连接级认证开关的既有 provider 测试和扩展实现。 */
  public EnterpriseConnectionAuthProfileRequest(
      String connectionId,
      String provider,
      String revision,
      String credentialRef,
      Map<String, Object> providerConfig) {
    this(connectionId, provider, revision, credentialRef, providerConfig, true);
  }

  public EnterpriseConnectionAuthProfileRequest {
    providerConfig = providerConfig == null ? Map.of() : Map.copyOf(providerConfig);
  }
}
