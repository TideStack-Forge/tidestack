package com.ouroboros.enterprise.feishu;

import java.util.Optional;

import com.ouroboros.security.externalauth.config.OAuth2ProviderSpec;
import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileProvider;
import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileRequest;

/** Derives the fixed Feishu user OAuth profile for a published connection. */
public final class FeishuEnterpriseAuthProfileProvider
    implements EnterpriseConnectionAuthProfileProvider {

  private static final String PROVIDER = "feishu";
  private static final String AUTHORIZATION_URI =
      "https://accounts.feishu.cn/open-apis/authen/v1/authorize";
  private static final String TOKEN_URI = "https://accounts.feishu.cn/oauth/v3/token";
  private static final String USER_INFO_URI =
      "https://open.feishu.cn/open-apis/authen/v1/user_info";

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public Optional<ProviderSpec> resolve(EnterpriseConnectionAuthProfileRequest request) {
    if (request == null || !PROVIDER.equals(request.provider())
        || request.connectionId() == null || request.connectionId().isBlank()
        || request.revision() == null || request.revision().isBlank()
        || request.credentialRef() == null || request.credentialRef().isBlank()) {
      return Optional.empty();
    }
    if (request.credentialRef().contains("#")) {
      return Optional.empty();
    }

    OAuth2ProviderSpec profile = new OAuth2ProviderSpec();
    profile.setKey(request.connectionId());
    profile.setType("oauth2");
    profile.setDisplayName("飞书");
    profile.setAuthorizationUri(AUTHORIZATION_URI);
    profile.setTokenUri(TOKEN_URI);
    profile.setUserInfoUri(USER_INFO_URI);
    profile.setUserInfoDataField("data");
    profile.setClientIdRef(request.credentialRef() + "#/appId");
    profile.setClientSecretRef(request.credentialRef() + "#/appSecret");
    profile.setScopes(java.util.List.of());
    profile.setExternalIdField("open_id");
    // 飞书是唯一一个"登录身份与审批成员标识同源"的渠道：官方审批要求 open_id。
    profile.setMemberIdField("open_id");
    profile.setPkceEnabled(true);
    profile.setPkceMethod("S256");
    profile.setConnectionScoped(true);
    profile.setConnectionProfileRevision(request.revision());
    return Optional.of(profile);
  }

}
