package com.ouroboros.ability.auth.oidc;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Registers client configuration independently of whether the OIDC protocol is enabled. */
@Configuration(proxyBeanMethods = false)
@Import({OidcClientConfigurationValidator.class, OidcLoginController.class, OidcConsentController.class, OidcServerAutoConfiguration.class,
    OidcUnavailableSecurityConfiguration.class,
    com.ouroboros.ability.auth.oidc.management.AuthClientManagementController.class})
public class OidcAutoConfiguration {
  @org.springframework.context.annotation.Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      prefix = "ouroboros.integration-management.migration", name = "enabled", havingValue = "true")
  public com.ouroboros.ability.auth.oidc.management.IntegrationAuthorityMigration integrationAuthorityMigration(
      com.ouroboros.security.rbac.service.RoleService roles,
      com.ouroboros.security.rbac.service.RoleAuthorityService authorities) {
    return new com.ouroboros.ability.auth.oidc.management.IntegrationAuthorityMigration(roles, authorities);
  }

  @org.springframework.context.annotation.Bean
  public com.ouroboros.ability.auth.oidc.management.OidcIntegrationContributor oidcIntegrationContributor() {
    return new com.ouroboros.ability.auth.oidc.management.OidcIntegrationContributor();
  }

  @org.springframework.context.annotation.Bean
  public com.ouroboros.ability.auth.oidc.registration.DcrCredentials dcrCredentials(org.springframework.beans.factory.ObjectProvider<com.ouroboros.cache.CacheManager> cache,
      org.springframework.beans.factory.ObjectProvider<org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings> settings) {
    return new com.ouroboros.ability.auth.oidc.registration.DcrCredentials(cache.getIfAvailable(),
        () -> settings.getIfAvailable() == null ? null : settings.getIfAvailable().getIssuer());
  }

  @org.springframework.context.annotation.Bean
  public com.ouroboros.ability.auth.oidc.management.OidcConfigurationManagementPolicy oidcConfigurationManagementPolicy(
      org.springframework.beans.factory.ObjectProvider<com.nimbusds.jose.jwk.JWKSet> signingKeys,
      org.springframework.beans.factory.ObjectProvider<org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings> serverSettings,
      com.ouroboros.ability.auth.oidc.registration.DcrCredentials credentials) {
    return new com.ouroboros.ability.auth.oidc.management.OidcConfigurationManagementPolicy(
        () -> signingKeys.getIfAvailable() != null && serverSettings.getIfAvailable() != null, credentials);
  }
}
