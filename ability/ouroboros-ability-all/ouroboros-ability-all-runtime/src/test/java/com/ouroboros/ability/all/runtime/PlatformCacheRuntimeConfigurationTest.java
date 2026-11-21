package com.ouroboros.ability.all.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.app.cache.PlatformCacheAdministrationConfiguration;
import com.ouroboros.cache.CacheAdministration;
import com.ouroboros.cache.management.CacheManagementAutoConfiguration;
import com.ouroboros.cache.management.controller.PlatformCacheController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformCacheRuntimeConfigurationTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(PlatformCacheAdministrationConfiguration.class, CacheManagementAutoConfiguration.class)
      .withBean(CacheManager.class, () -> new ConcurrentMapCacheManager("users"));

  @Test
  void ordinaryRuntimeServesCacheManagementWithoutDevelopmentPlatform() {
    runner.withPropertyValues("ouroboros.platform.cache.token.secret=runtime-test-secret").run(context -> {
      assertThat(context).hasSingleBean(CacheAdministration.class).hasSingleBean(PlatformCacheController.class);
      context.getBean(CacheManager.class).getCache("users").put("alice", "Alice");
      var mvc = MockMvcBuilders.standaloneSetup(context.getBean(PlatformCacheController.class)).build();

      mvc.perform(get("/api/platform-cache/summary"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(0))
          .andExpect(jsonPath("$.data.cacheManagerType").value("ConcurrentMapCacheManager"));
      mvc.perform(get("/api/platform-cache/entries").param("cacheName", "users"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(0))
          .andExpect(jsonPath("$.data.total").value(1));
    });
  }

  @Test
  void ordinaryRuntimeStillRequiresCacheTokenSecret() {
    runner.run(context -> assertThat(context).hasFailed()
        .getFailure().hasRootCauseMessage("Platform cache token secret is required"));
  }
}
