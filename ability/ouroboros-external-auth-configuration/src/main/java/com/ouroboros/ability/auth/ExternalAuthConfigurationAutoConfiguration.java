package com.ouroboros.ability.auth;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ouroboros.ability.auth.config.ExternalAuthConfigurationValidator;
import com.ouroboros.ability.auth.config.UnifiedExternalAuthConfiguration;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.security.OuroborosSecurityAutoConfiguration;
import com.ouroboros.security.externalauth.ExternalAuthAutoConfiguration;
import com.ouroboros.security.externalauth.config.ExternalAuthConfiguration;

/** Wires the configuration ability into the existing authentication runtime contract. */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore({OuroborosSecurityAutoConfiguration.class, ExternalAuthAutoConfiguration.class})
@Import({ExternalAuthConfigurationValidator.class,
    com.ouroboros.ability.auth.config.ExternalAuthManagementController.class})
public class ExternalAuthConfigurationAutoConfiguration {
  @Bean
  public com.ouroboros.ability.auth.config.ExternalAuthConfigurationManagementPolicy externalAuthManagementPolicy() {
    return new com.ouroboros.ability.auth.config.ExternalAuthConfigurationManagementPolicy();
  }

  @Bean
  public com.ouroboros.ability.auth.config.ExternalAuthIntegrationContributor externalAuthIntegrationContributor() {
    return new com.ouroboros.ability.auth.config.ExternalAuthIntegrationContributor();
  }

  @Bean
  public ExternalAuthConfiguration externalAuthConfiguration(ConfigurationService configuration) {
    return new UnifiedExternalAuthConfiguration(configuration);
  }
}
