package com.ouroboros.dev.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.runtime.DevRuntimeCoreAutoConfiguration;
import com.ouroboros.dev.runtime.service.DefaultRuntimeLockService;
import com.ouroboros.dev.runtime.service.PersistentRuntimeLockStore;
import com.ouroboros.dev.runtime.service.RuntimeLockService;
import com.ouroboros.dev.runtime.service.RuntimeLockStore;
import com.ouroboros.vcs.VCSManagerCenter;

class DevAppRuntimeLockAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, DevAppRuntimeLockAutoConfiguration.class))
      .withUserConfiguration(SupportConfiguration.class);

  @Test
  void shouldUsePersistentRuntimeLockStoreWhenRuntimeCoreAndAppLockAutoConfigsAreLoaded() throws Exception {
    contextRunner.run(context -> {
      assertEquals(1, context.getBeansOfType(RuntimeLockStore.class).size());
      RuntimeLockStore lockStore = context.getBean(RuntimeLockStore.class);
      assertTrue(lockStore instanceof PersistentRuntimeLockStore);

      RuntimeLockService lockService = context.getBean(RuntimeLockService.class);
      Field lockStoreField = DefaultRuntimeLockService.class.getDeclaredField("lockStore");
      lockStoreField.setAccessible(true);
      Object lockStoreInService = lockStoreField.get(lockService);
      assertSame(lockStore, lockStoreInService);
    });
  }

  @Configuration
  static class SupportConfiguration {
    @Bean
    VCSManagerCenter vcsManagerCenter() {
      return mock(VCSManagerCenter.class);
    }

    @Bean
    AppHomeResolver appHomeResolver() {
      return appName -> "/tmp/" + appName;
    }
  }
}
