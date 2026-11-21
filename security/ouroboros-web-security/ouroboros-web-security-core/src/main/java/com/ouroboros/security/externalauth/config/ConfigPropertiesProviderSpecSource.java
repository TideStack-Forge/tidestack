package com.ouroboros.security.externalauth.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads provider specs from Spring ConfigurationProperties.
 */
public class ConfigPropertiesProviderSpecSource implements ExternalAuthProviderSpecSource {

  private final java.util.function.Supplier<ExternalAuthProperties> properties;
  private final java.util.function.Supplier<String> revision;

  public ConfigPropertiesProviderSpecSource(ExternalAuthProperties properties) {
    this.properties = () -> properties;
    this.revision = () -> null;
  }

  public ConfigPropertiesProviderSpecSource(ExternalAuthConfiguration configuration) {
    this.properties = configuration::get;
    this.revision = configuration::getRevision;
  }

  @Override
  public java.util.Optional<String> getRevision() {
    return java.util.Optional.ofNullable(revision.get());
  }

  @Override
  public List<ProviderSpec> getProviderSpecs() {
    ExternalAuthProperties properties = this.properties.get();
    ArrayList<ProviderSpec> specs = new ArrayList<ProviderSpec>();
    for (LdapProviderSpec ldapSpec : properties.getLdap()) {
      ldapSpec.setType("ldap");
      specs.add(ldapSpec);
    }
    for (OAuth2ProviderSpec oauth2Spec : properties.getOauth2()) {
      oauth2Spec.setType("oauth2");
      specs.add(oauth2Spec);
    }
    for (CasProviderSpec casSpec : properties.getCas()) {
      casSpec.setType("cas");
      specs.add(casSpec);
    }
    return specs;
  }
}
