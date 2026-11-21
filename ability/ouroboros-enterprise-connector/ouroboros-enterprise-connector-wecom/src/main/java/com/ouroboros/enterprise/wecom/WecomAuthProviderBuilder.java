package com.ouroboros.enterprise.wecom;

import java.util.Optional;

import org.springframework.web.client.RestTemplate;

import com.ouroboros.security.externalauth.ExternalAuthProvider;
import com.ouroboros.security.externalauth.ExternalAuthProviderBuildContext;
import com.ouroboros.security.externalauth.ExternalAuthProviderBuilder;
import com.ouroboros.security.externalauth.config.ProviderSpec;

/** Builds the Enterprise WeChat login bridge from the connector credential reference. */
public final class WecomAuthProviderBuilder implements ExternalAuthProviderBuilder {

  private final RestTemplate restTemplate;
  private final WecomTokenCache tokenCache;
  private final long overallTimeoutMillis;

  public WecomAuthProviderBuilder(RestTemplate restTemplate) {
    this(restTemplate, new WecomTokenCache());
  }

  public WecomAuthProviderBuilder(RestTemplate restTemplate, WecomTokenCache tokenCache) {
    this(restTemplate, tokenCache, 15_000L);
  }

  public WecomAuthProviderBuilder(RestTemplate restTemplate, WecomTokenCache tokenCache, long overallTimeoutMillis) {
    if (overallTimeoutMillis < 1) { throw new IllegalArgumentException("Enterprise login timeout must be positive"); }
    this.overallTimeoutMillis = overallTimeoutMillis;
    this.restTemplate = restTemplate;
    this.tokenCache = tokenCache;
  }

  @Override
  public String type() {
    return WecomEnterpriseAuthProfileProvider.AUTH_TYPE;
  }

  @Override
  public Optional<ExternalAuthProvider> build(
      ProviderSpec spec, ExternalAuthProviderBuildContext context) {
    if (!(spec instanceof WecomAuthBridgeProviderSpec bridgeSpec)
        || bridgeSpec.getCredentialRef() == null
        || bridgeSpec.getCredentialRef().isBlank()
        || bridgeSpec.getCorpId() == null || bridgeSpec.getCorpId().isBlank()) {
      return Optional.empty();
    }
    Optional<String> payload = context.resolveSecret(bridgeSpec.getCredentialRef());
    if (payload.isEmpty()) {
      return Optional.empty();
    }
    WecomCredential credential = WecomCredential.fromSecretPayload(payload.get());
    if (credential.isSelfBuilt()) {
      return Optional.of(new WecomSelfBuiltAuthProvider(bridgeSpec, credential, restTemplate,
          new WecomApplicationTokens(restTemplate, tokenCache), context.publicBaseUrl(), overallTimeoutMillis));
    }
    return Optional.of(new WecomEnterpriseAuthProvider(
        bridgeSpec, credential, restTemplate, tokenCache, context.publicBaseUrl(), overallTimeoutMillis));
  }
}
