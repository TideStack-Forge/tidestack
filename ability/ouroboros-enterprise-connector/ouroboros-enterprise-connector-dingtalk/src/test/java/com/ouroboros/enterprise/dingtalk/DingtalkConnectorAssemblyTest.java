package com.ouroboros.enterprise.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.enterprise.runtime.EnterpriseConnectorAutoConfiguration;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.enterprise.runtime.auth.EnterpriseConnectionAuthProfileResolver;
import com.ouroboros.security.externalauth.ExternalAuthAutoConfiguration;

class DingtalkConnectorAssemblyTest {
  @Test
  void providerAndLoginInfrastructureStartWithoutDomainAdaptersOrRemoteCalls() {
    var configuration = mock(ConfigurationService.class);
    new ApplicationContextRunner().withUserConfiguration(ExternalAuthAutoConfiguration.class,
        EnterpriseConnectorAutoConfiguration.class, DingtalkConnectorAutoConfiguration.class)
        .withBean(ConfigurationService.class, () -> configuration)
        .withBean(com.ouroboros.configuration.management.ConfigurationManagementService.class,
            () -> mock(com.ouroboros.configuration.management.ConfigurationManagementService.class))
        .run(context -> {
          assertThat(context).hasSingleBean(EnterpriseConnectionCatalog.class)
              .hasSingleBean(EnterpriseConnectionAuthProfileResolver.class).hasSingleBean(DingtalkTokenCache.class);
          assertThat(context.getBean(EnterpriseConnectionProviderRegistry.class).providers())
              .extracting(provider -> provider.provider()).containsExactly("dingtalk");
          verifyNoInteractions(configuration);
        });
  }
}
