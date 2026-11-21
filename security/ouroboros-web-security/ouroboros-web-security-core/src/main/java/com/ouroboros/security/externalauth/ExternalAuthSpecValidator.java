package com.ouroboros.security.externalauth;

import java.util.HashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ouroboros.security.externalauth.config.*;

public class ExternalAuthSpecValidator {
  private static final Logger logger = LoggerFactory.getLogger(ExternalAuthSpecValidator.class);

  public static void validate(ExternalAuthProperties properties, List<ProviderSpec> specs) {
    requirePublicBaseUrlForCallbackFlows(properties, specs);
    var keys = new HashSet<String>();
    for (ProviderSpec spec : specs) {
      if (spec.getKey() == null || !spec.getKey().matches("[A-Za-z0-9][A-Za-z0-9._-]*") || !keys.add(spec.getKey())
          || spec.isConnectionScoped()) {
        throw new IllegalStateException("认证渠道 key 无效、重复或与连接作用域混用");
      }
      if (!spec.isEnabled()) { continue; }
      if (spec instanceof LdapProviderSpec ldap) { validateLdapSpec(ldap); }
      if (spec instanceof OAuth2ProviderSpec oauth) { validateOAuth2Spec(oauth); }
      if (spec instanceof CasProviderSpec cas) { validateCasSpec(cas); }
    }
    var limits = properties.getBindingRateLimit();
    RateLimitThreshold.parse(limits.getByExternalId());
    RateLimitThreshold.parse(limits.getByUsername());
    if (limits.isSourceIpEnabled()) { RateLimitThreshold.parse(limits.getBySourceIp()); }
    if (limits.getProviders() != null) {
      limits.getProviders().values().forEach(limit -> {
        if (limit.getByExternalId() != null) { RateLimitThreshold.parse(limit.getByExternalId()); }
        if (limit.getByUsername() != null) { RateLimitThreshold.parse(limit.getByUsername()); }
        if (limit.getBySourceIp() != null) { RateLimitThreshold.parse(limit.getBySourceIp()); }
      });
    }
  }

  private static boolean hasText(String value) { return value != null && !value.isBlank(); }

  private static void requirePublicBaseUrlForCallbackFlows(ExternalAuthProperties properties, List<ProviderSpec> specs) {
    boolean callbackFlowConfigured = specs.stream()
        .filter(ProviderSpec::isEnabled)
        .anyMatch(spec -> "oauth2".equals(spec.getType()) || "cas".equals(spec.getType()));
    if (!callbackFlowConfigured) {
      return;
    }
    if (!hasText(properties.getPublicBaseUrl())) {
      throw new IllegalStateException(
          "ouroboros.security.external-auth.public-base-url 未配置：OAuth2/CAS 回调地址无法拼接");
    }
  }

  public static void validateLdapSpec(LdapProviderSpec spec) {
    if (spec.getUrls() == null || spec.getUrls().isEmpty()) {
      throw new IllegalStateException("LDAP provider '" + spec.getKey() + "' has no URLs configured");
    }
    if (spec.getBaseDn() == null || spec.getBaseDn().isEmpty()) {
      throw new IllegalStateException("LDAP provider '" + spec.getKey() + "' has no baseDn configured");
    }
    if (spec.getExternalIdAttribute() == null || spec.getExternalIdAttribute().isEmpty()) {
      throw new IllegalStateException("LDAP provider '" + spec.getKey()
          + "' has no externalIdAttribute configured — a stable external ID is mandatory");
    }
    // Warn about plaintext LDAP URLs
    boolean allPlaintext = spec.getUrls().stream().allMatch(url -> url.startsWith("ldap://"));
    if (allPlaintext) {
      logger.warn("LDAP provider '{}' uses plaintext ldap:// URLs without TLS — credentials may be exposed in transit",
          spec.getKey());
    }
  }

  public static void validateCasSpec(CasProviderSpec spec) {
    if (spec.getCasBaseUrl() == null || spec.getCasBaseUrl().isEmpty()) {
      throw new IllegalStateException("CAS provider '" + spec.getKey() + "' has no casBaseUrl configured");
    }
  }

  public static void validateOAuth2Spec(OAuth2ProviderSpec spec) {
    if (!hasText(spec.getAuthorizationUri())) {
      throw new IllegalStateException("OAuth2 provider '" + spec.getKey() + "' has no authorizationUri configured");
    }
    if (!hasText(spec.getTokenUri())) {
      throw new IllegalStateException("OAuth2 provider '" + spec.getKey() + "' has no tokenUri configured");
    }
    if (!hasText(spec.getClientId()) && !hasText(spec.getClientIdRef())) {
      throw new IllegalStateException("OAuth2 provider '" + spec.getKey() + "' has no clientId configured");
    }
    if (hasText(spec.getClientId()) && hasText(spec.getClientIdRef())) {
      throw new IllegalStateException("OAuth2 provider '" + spec.getKey()
          + "' must configure exactly one of clientId or clientIdRef");
    }
    if (spec.isConnectionScoped() && hasText(spec.getClientSecret())) {
      throw new IllegalStateException("Connection-scoped OAuth2 provider '" + spec.getKey()
          + "' must not contain an inline clientSecret");
    }
    if (spec.isConnectionScoped() && !hasText(spec.getConnectionProfileRevision())) {
      throw new IllegalStateException("Connection-scoped OAuth2 provider '" + spec.getKey()
          + "' has no connectionProfileRevision configured");
    }
    if (!hasText(spec.getExternalIdField())) {
      throw new IllegalStateException("OAuth2 provider '" + spec.getKey()
          + "' has no externalIdField configured — a stable external ID is mandatory");
    }
    if (!hasText(spec.getUserInfoUri()) && !hasText(spec.getJwksUri())) {
      throw new IllegalStateException("OAuth2 provider '" + spec.getKey()
          + "' has no identity source configured (userInfoUri or jwksUri)");
    }
    if (hasText(spec.getJwksUri()) && !hasText(spec.getIssuer())) {
      throw new IllegalStateException("OAuth2 provider '" + spec.getKey()
          + "' has jwksUri configured without issuer");
    }
  }

}
