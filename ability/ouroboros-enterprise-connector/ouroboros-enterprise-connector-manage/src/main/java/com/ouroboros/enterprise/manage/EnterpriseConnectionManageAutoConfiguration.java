package com.ouroboros.enterprise.manage;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.enterprise.runtime.EnterpriseConnectorAutoConfiguration;
import com.ouroboros.security.OuroborosSecurityAutoConfiguration;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({EnterpriseConnectorAutoConfiguration.class, OuroborosSecurityAutoConfiguration.class})
@Import({EnterpriseConnectionManagementSchemaController.class, EnterpriseWorkbenchManagementController.class})
public class EnterpriseConnectionManageAutoConfiguration {
  @Bean
  EnterpriseConnectionIntegrationContributor enterpriseConnectionIntegrationContributor(EnterpriseConnectionProviderRegistry providers) {
    return new EnterpriseConnectionIntegrationContributor(providers);
  }

  @Bean
  EnterpriseWorkbenchIntegrationContributor enterpriseWorkbenchIntegrationContributor() {
    return new EnterpriseWorkbenchIntegrationContributor();
  }
}
