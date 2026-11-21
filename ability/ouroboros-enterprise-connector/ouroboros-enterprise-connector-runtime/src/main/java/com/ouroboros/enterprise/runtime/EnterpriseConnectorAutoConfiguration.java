package com.ouroboros.enterprise.runtime;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;
import com.ouroboros.enterprise.runtime.auth.EnterpriseConnectionAuthProfileResolver;
import com.ouroboros.util.SPI;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.enterprise.EnterpriseConnectionCredentialMapper;
import com.ouroboros.enterprise.runtime.credential.*;
import com.ouroboros.enterprise.runtime.management.*;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EnterpriseCredentialKeys.class)
@Import({EnterpriseConnectionCatalog.class, EnterpriseConnectionConfigurationValidator.class,
    EnterpriseConnectionAuthProfileResolver.class,
    com.ouroboros.enterprise.runtime.web.EnterpriseConnectionProviderController.class,
    EnterpriseConnectionManagementController.class,
    EnterpriseConnectionVerificationController.class,
    com.ouroboros.enterprise.runtime.callback.EnterpriseCallbackController.class})
public class EnterpriseConnectorAutoConfiguration {
  @Bean
  com.ouroboros.enterprise.EnterpriseApiBudget enterpriseApiBudget(ConfigurationService configuration,
      @org.springframework.beans.factory.annotation.Value("${ouroboros.enterprise.execution-nodes:1}") int executionNodes) {
    return new ConfiguredEnterpriseApiBudget(configuration, executionNodes);
  }
  @Bean
  com.ouroboros.enterprise.runtime.callback.EnterpriseCallbackDispatcher enterpriseCallbackDispatcher(
      EnterpriseConnectionCatalog connections,
      List<com.ouroboros.enterprise.callback.EnterpriseCallbackProtocol> protocols,
      List<com.ouroboros.enterprise.callback.EnterpriseCallbackSubscriber> subscribers) {
    return new com.ouroboros.enterprise.runtime.callback.EnterpriseCallbackDispatcher(connections, protocols, subscribers);
  }

  @Bean
  EnterpriseCredentialCipher enterpriseCredentialCipher(EnterpriseCredentialKeys keys) {
    return new EnterpriseCredentialCipher(keys.activeKeyId(), keys.keys());
  }

  @Bean
  EnterpriseManagedCredentials enterpriseManagedCredentials(ConfigurationService configuration, EnterpriseCredentialCipher cipher) {
    return new EnterpriseManagedCredentials(configuration, cipher);
  }

  @Bean
  ManagedEnterpriseSecretResolver managedEnterpriseSecretResolver(EnterpriseManagedCredentials credentials) {
    return new ManagedEnterpriseSecretResolver(credentials);
  }

  @Bean
  EnterpriseConnectionManagementPolicy enterpriseConnectionManagementPolicy(EnterpriseConnectionProviderRegistry providers,
      EnterpriseConnectionConfigurationValidator validator, EnterpriseManagedCredentials credentials,
      List<EnterpriseConnectionCredentialMapper> mappers, CompositeSecretResolver resolver) {
    return new EnterpriseConnectionManagementPolicy(providers, validator, credentials, mappers, resolver);
  }

  @Bean
  EnterpriseConnectionProviderRegistry enterpriseConnectionProviderRegistry(List<EnterpriseConnectionProvider> contributions) {
    var providers = new ArrayList<EnterpriseConnectionProvider>();
    SPI.getServiceStream(EnterpriseConnectionProvider.class).forEach(providers::add);
    providers.addAll(contributions);
    return new EnterpriseConnectionProviderRegistry(providers);
  }
}
