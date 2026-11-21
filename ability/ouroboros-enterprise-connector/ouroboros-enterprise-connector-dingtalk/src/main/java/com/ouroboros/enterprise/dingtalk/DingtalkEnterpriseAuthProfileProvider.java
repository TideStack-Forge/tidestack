package com.ouroboros.enterprise.dingtalk;

import java.util.Optional;

import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileProvider;
import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileRequest;

/** Derives a connection-scoped DingTalk login profile from the published connector snapshot. */
public final class DingtalkEnterpriseAuthProfileProvider
    implements EnterpriseConnectionAuthProfileProvider {

  public static final String PROVIDER = "dingtalk";
  public static final String AUTH_TYPE = "dingtalk-enterprise";

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public Optional<ProviderSpec> resolve(EnterpriseConnectionAuthProfileRequest request) {
    if (request == null || !PROVIDER.equals(request.provider())
        || blank(request.connectionId()) || blank(request.revision())
        || blank(request.credentialRef()) || request.credentialRef().contains("#")) {
      return Optional.empty();
    }
    String corpId = text(request.providerConfig().get("corpId"));
    if (blank(corpId)) {
      return Optional.empty();
    }

    DingtalkAuthBridgeProviderSpec profile = new DingtalkAuthBridgeProviderSpec();
    profile.setKey(request.connectionId());
    profile.setType(AUTH_TYPE);
    profile.setDisplayName("钉钉");
    profile.setCredentialRef(request.credentialRef());
    profile.setCorpId(corpId);
    profile.setConnectionScoped(true);
    profile.setConnectionProfileRevision(request.revision());
    return Optional.of(profile);
  }

  private String text(Object value) {
    if (value == null) {
      return null;
    }
    String result = String.valueOf(value).trim();
    return result.isEmpty() ? null : result;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
