package com.ouroboros.enterprise.wecom;

import java.util.Optional;

import com.ouroboros.security.externalauth.config.ProviderSpec;
import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileProvider;
import com.ouroboros.enterprise.auth.EnterpriseConnectionAuthProfileRequest;

/** Derives the Enterprise WeChat third-party web authorization profile. */
public final class WecomEnterpriseAuthProfileProvider
    implements EnterpriseConnectionAuthProfileProvider {

  public static final String PROVIDER = "wecom";
  public static final String AUTH_TYPE = "wecom-enterprise";

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

    WecomAuthBridgeProviderSpec profile = new WecomAuthBridgeProviderSpec();
    profile.setKey(request.connectionId());
    profile.setType(AUTH_TYPE);
    profile.setDisplayName("企业微信");
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
