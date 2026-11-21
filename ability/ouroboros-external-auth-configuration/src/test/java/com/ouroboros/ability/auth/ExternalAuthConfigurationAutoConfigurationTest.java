package com.ouroboros.ability.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.ouroboros.ability.auth.config.UnifiedExternalAuthConfiguration;
import com.ouroboros.ability.auth.support.MutableAuthConfiguration;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.cache.CacheManager;
import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.security.externalauth.ExternalAuthProviderFactory;
import com.ouroboros.security.externalauth.ExternalAuthAutoConfiguration;
import com.ouroboros.security.externalauth.config.ExternalAuthConfiguration;
import com.ouroboros.security.externalauth.config.ExternalAuthProviderSpecSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

class ExternalAuthConfigurationAutoConfigurationTest {
  @Test
  void suppliesTheRuntimeContractWithoutReadingMetadataBeforeThePlatformLifecycleIsReady() {
    var values = new MutableAuthConfiguration();
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ExternalAuthConfigurationAutoConfiguration.class, ExternalAuthAutoConfiguration.class))
        .withBean(ConfigurationService.class, () -> values.service)
        .withBean(com.ouroboros.configuration.management.ConfigurationManagementService.class,
            () -> org.mockito.Mockito.mock(com.ouroboros.configuration.management.ConfigurationManagementService.class))
        .withBean(CacheManager.class, MemoryCacheManager::new)
        .withBean(ExternalAuthProviderFactory.class)
        .run(context -> {
          assertThat(context).hasNotFailed().hasSingleBean(ExternalAuthConfiguration.class);
          assertThat(context.getBean(ExternalAuthConfiguration.class)).isInstanceOf(UnifiedExternalAuthConfiguration.class);
          verifyNoInteractions(values.service);
          values.put(UnifiedExternalAuthConfiguration.KEY, "{}");
          assertThat(context.getBean(ExternalAuthProviderSpecSource.class).getProviderSpecs()).isEmpty();
          assertThat(context.getBean(ExternalAuthProviderFactory.class).getAllProviders()).isEmpty();
        });
  }
}
