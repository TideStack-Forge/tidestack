package com.ouroboros.security.externalauth;

import java.util.ArrayList;
import java.util.List;

import com.ouroboros.security.externalauth.config.ExternalAuthConfiguration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.ouroboros.security.externalauth.config.ConfigPropertiesProviderSpecSource;
import com.ouroboros.security.externalauth.config.ExternalAuthProperties;
import com.ouroboros.security.externalauth.config.ExternalAuthProviderSpecSource;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.security.externalauth.secret.ConfigSecretResolver;
import com.ouroboros.security.externalauth.secret.EnvSecretResolver;
import com.ouroboros.security.externalauth.secret.FileSecretResolver;
import com.ouroboros.security.externalauth.secret.SecretResolver;

/**
 * Auto-configuration for external authentication support.
 */
@Configuration
@EnableConfigurationProperties(ExternalAuthProperties.class)
public class ExternalAuthAutoConfiguration {

  @Bean
  public CompositeSecretResolver compositeSecretResolver(Environment environment, List<SecretResolver> contributions) {
    var resolvers = new ArrayList<SecretResolver>();
    resolvers.add(new EnvSecretResolver(environment));
    resolvers.add(new ConfigSecretResolver(environment));
    resolvers.add(new FileSecretResolver());
    resolvers.addAll(contributions);
    return new CompositeSecretResolver(resolvers);
  }

  @Bean
  @ConditionalOnMissingBean(ExternalAuthConfiguration.class)
  public ExternalAuthConfiguration externalAuthConfiguration(ExternalAuthProperties properties) {
    return () -> properties;
  }

  @Bean
  public ExternalAuthProviderSpecSource externalAuthProviderSpecSource(ExternalAuthConfiguration configuration) {
    return new ConfigPropertiesProviderSpecSource(configuration);
  }
}
