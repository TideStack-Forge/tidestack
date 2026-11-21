package com.ouroboros.enterprise.dingtalk;

import java.util.Optional;

import org.springframework.web.client.RestTemplate;

import com.ouroboros.security.externalauth.ExternalAuthProvider;
import com.ouroboros.security.externalauth.ExternalAuthProviderBuildContext;
import com.ouroboros.security.externalauth.ExternalAuthProviderBuilder;
import com.ouroboros.security.externalauth.config.ProviderSpec;

/** Builds a DingTalk login bridge only from a published connection credential reference. */
public final class DingtalkAuthProviderBuilder implements ExternalAuthProviderBuilder {

  private final RestTemplate restTemplate;
  private final DingtalkTokenCache tokenCache;
  private final long overallTimeoutMillis;

  public DingtalkAuthProviderBuilder(RestTemplate restTemplate, DingtalkTokenCache tokenCache) {
    this(restTemplate, tokenCache, 15_000L);
  }

  public DingtalkAuthProviderBuilder(RestTemplate restTemplate, DingtalkTokenCache tokenCache, long overallTimeoutMillis) {
    if (overallTimeoutMillis < 1) { throw new IllegalArgumentException("Enterprise login timeout must be positive"); }
    this.overallTimeoutMillis = overallTimeoutMillis;
    this.restTemplate = restTemplate;
    this.tokenCache = tokenCache;
  }

  @Override
  public String type() {
    return DingtalkEnterpriseAuthProfileProvider.AUTH_TYPE;
  }

  @Override
  public Optional<ExternalAuthProvider> build(
      ProviderSpec spec, ExternalAuthProviderBuildContext context) {
    if (!(spec instanceof DingtalkAuthBridgeProviderSpec bridgeSpec)
        || bridgeSpec.getCredentialRef() == null
        || bridgeSpec.getCredentialRef().isBlank()
        || bridgeSpec.getCorpId() == null
        || bridgeSpec.getCorpId().isBlank()) {
      return Optional.empty();
    }
    Optional<String> payload = context.resolveSecret(bridgeSpec.getCredentialRef());
    if (payload.isEmpty()) {
      return Optional.empty();
    }
    DingtalkCredential credential = DingtalkCredential.fromSecretPayload(payload.get());
    DingtalkMemberIdResolver memberIdResolver = new DingtalkMemberIdResolver(
        restTemplate, new com.ouroboros.enterprise.ResolvedEnterpriseConnection(bridgeSpec.getKey(),
            java.util.Map.of("provider", "dingtalk", "corpId", bridgeSpec.getCorpId(),
                "revision", java.util.Objects.toString(bridgeSpec.getConnectionProfileRevision(), "")), credential), tokenCache);
    return Optional.of(new DingtalkEnterpriseAuthProvider(
        bridgeSpec, credential, restTemplate, memberIdResolver, context.publicBaseUrl(), overallTimeoutMillis));
  }
}
