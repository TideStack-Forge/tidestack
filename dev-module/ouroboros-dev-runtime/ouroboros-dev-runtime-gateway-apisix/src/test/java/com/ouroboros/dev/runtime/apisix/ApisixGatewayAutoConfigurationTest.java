package com.ouroboros.dev.runtime.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.SpringFactoriesLoader;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.DevRuntimeCoreAutoConfiguration;
import com.ouroboros.dev.runtime.apisix.client.ApisixAdminClient;
import com.ouroboros.dev.runtime.apisix.command.ApisixGatewayRouteSyncConsumer;
import com.ouroboros.dev.runtime.apisix.config.ApisixGatewayProperties;
import com.ouroboros.dev.runtime.apisix.mapper.ApisixRouteMapper;
import com.ouroboros.dev.runtime.apisix.model.ApisixRoutePayload;

class ApisixGatewayAutoConfigurationTest {

  @Test
  void shouldDiscoverAutoConfigurationThroughSpringFactories() {
    List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(
        EnableAutoConfiguration.class,
        Thread.currentThread().getContextClassLoader()
    );

    assertTrue(factoryNames.contains(ApisixGatewayAutoConfiguration.class.getName()));
  }

  @Test
  void shouldNotCreateApisixBeansWhenGatewayIsDisabled() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, ApisixGatewayAutoConfiguration.class))
        .run(context -> {
          assertEquals(0, context.getBeansOfType(ApisixGatewayRouteSyncConsumer.class).size());
          assertEquals(0, context.getBeansOfType(ApisixAdminClient.class).size());
          assertEquals(0, context.getBeansOfType(ApisixRouteMapper.class).size());
        });
  }

  @Test
  void shouldNotCreateApisixBeansWhenProviderDoesNotMatch() {
    new ApplicationContextRunner()
        .withPropertyValues(
            "ouroboros.dev.runtime.gateway.enabled=true",
            "ouroboros.dev.runtime.gateway.provider=kong",
            "ouroboros.dev.runtime.gateway.apisix.base-url=http://127.0.0.1:9180",
            "ouroboros.dev.runtime.gateway.apisix.admin-key=secret",
            "ouroboros.dev.runtime.gateway.apisix.route-id-prefix=rtg"
        )
        .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, ApisixGatewayAutoConfiguration.class))
        .run(context -> {
          assertEquals(0, context.getBeansOfType(ApisixGatewayRouteSyncConsumer.class).size());
          assertEquals(0, context.getBeansOfType(ApisixAdminClient.class).size());
        });
  }

  @Test
  void shouldCreateApisixBeansWhenGatewayIsEnabledAndConfigured() {
    new ApplicationContextRunner()
        .withPropertyValues(
            "ouroboros.dev.runtime.gateway.enabled=true",
            "ouroboros.dev.runtime.gateway.provider=apisix",
            "ouroboros.dev.runtime.gateway.apisix.base-url=http://127.0.0.1:9180",
            "ouroboros.dev.runtime.gateway.apisix.admin-key=secret",
            "ouroboros.dev.runtime.gateway.apisix.route-id-prefix=rtg"
        )
        .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, ApisixGatewayAutoConfiguration.class))
        .run(context -> {
          assertEquals(1, context.getBeansOfType(ApisixGatewayRouteSyncConsumer.class).size());
          assertEquals(1, context.getBeansOfType(ApisixAdminClient.class).size());
          assertEquals(1, context.getBeansOfType(ApisixRouteMapper.class).size());
          assertEquals("rtg", context.getBean(ApisixGatewayProperties.class).getRouteIdPrefix());
        });
  }

  @Test
  void shouldLetUserProvidedApisixAdminClientOverrideDefaultBean() {
    new ApplicationContextRunner()
        .withPropertyValues(
            "ouroboros.dev.runtime.gateway.enabled=true",
            "ouroboros.dev.runtime.gateway.provider=apisix",
            "ouroboros.dev.runtime.gateway.apisix.base-url=http://127.0.0.1:9180",
            "ouroboros.dev.runtime.gateway.apisix.admin-key=secret",
            "ouroboros.dev.runtime.gateway.apisix.route-id-prefix=rtg"
        )
        .withUserConfiguration(CustomApisixClientConfiguration.class)
        .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, ApisixGatewayAutoConfiguration.class))
        .run(context -> assertEquals(CustomApisixAdminClient.class, context.getBean(ApisixAdminClient.class).getClass()));
  }

  @Configuration
  static class CustomApisixClientConfiguration {
    @Bean
    ApisixAdminClient apisixAdminClient() {
      return new CustomApisixAdminClient();
    }
  }

  static class CustomApisixAdminClient implements ApisixAdminClient {
    @Override
    public Try<Void> upsertRoute(String routeId, ApisixRoutePayload routePayload) {
      return Try.success(null);
    }

    @Override
    public Try<Void> deleteRoute(String routeId) {
      return Try.success(null);
    }
  }
}
