package com.ouroboros.dev.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommand;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncPublisher;
import com.ouroboros.dev.runtime.gateway.host.StableDomainHostResolver;
import com.ouroboros.dev.runtime.gateway.RuntimeGatewaySyncListener;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.service.DebugSessionStore;
import com.ouroboros.dev.runtime.service.InMemoryDebugSessionStore;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeLockStore;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeStateStore;
import com.ouroboros.dev.runtime.service.RuntimeLockStore;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;

class DevRuntimeCoreAutoConfigurationTest {

  @Test
  void shouldDiscoverAutoConfigurationThroughSpringFactories() {
    try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
        .web(WebApplicationType.NONE)
        .properties("ouroboros.dev.runtime.default-backend=KUBERNETES")
        .run()) {
      assertTrue(context.containsBean("devRuntimeService"));
      assertTrue(context.containsBean("runtimeBackendResolver"));
      assertTrue(context.containsBean("devRuntimeEventPublisher"));
      assertTrue(context.containsBean("gatewayRouteSyncPublisher"));
      assertTrue(context.containsBean("stableDomainHostResolver"));
      assertTrue(context.containsBean("runtimeStateStore"));
      assertTrue(context.containsBean("runtimeLockStore"));
      assertTrue(context.containsBean("debugSessionStore"));
      assertEquals(BackendType.KUBERNETES, context.getBean(DevRuntimeProperties.class).getDefaultBackend());
      assertEquals(Integer.valueOf(1800), context.getBean(DevRuntimeProperties.class).getDebug().getMaxLifetimeSeconds());
      assertEquals(Integer.valueOf(600), context.getBean(DevRuntimeProperties.class).getDebug().getIdleTimeoutSeconds());
      assertEquals("127.0.0.1", context.getBean(DevRuntimeProperties.class).getDebug().getBrokerHost());
      assertEquals(DebugSessionBindingScope.LOOPBACK_ONLY, context.getBean(DevRuntimeProperties.class).getDebug().getBindScope());
      assertEquals(Boolean.FALSE, context.getBean(DevRuntimeProperties.class).getGateway().getEnabled());
    }
  }

  @Test
  void shouldLetUserProvidedRuntimeStateStoreOverrideDefaultBean() {
    new ApplicationContextRunner()
        .withUserConfiguration(OverrideStateStoreConfiguration.class)
        .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class))
        .run(context -> assertEquals(CustomRuntimeStateStore.class, context.getBean(RuntimeStateStore.class).getClass()));
  }

  @Test
  void shouldProvideDefaultInMemoryDebugSessionStore() {
    new ApplicationContextRunner()
        .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class))
        .run(context -> assertEquals(InMemoryDebugSessionStore.class, context.getBean(DebugSessionStore.class).getClass()));
  }

  @Test
  void shouldProvideDefaultInMemoryRuntimeLockStore() {
    new ApplicationContextRunner()
        .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class))
        .run(context -> assertEquals(InMemoryRuntimeLockStore.class, context.getBean(RuntimeLockStore.class).getClass()));
  }

  @Test
  void shouldProvideDefaultGatewaySupportBeansAndBindProperties() {
    new ApplicationContextRunner()
        .withPropertyValues(
            "ouroboros.dev.runtime.gateway.enabled=true",
            "ouroboros.dev.runtime.gateway.provider=apisix",
            "ouroboros.dev.runtime.gateway.stable-domain-host-suffix=develop.example.com"
        )
        .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class))
        .run(context -> {
          assertEquals(1, context.getBeansOfType(GatewayRouteSyncPublisher.class).size());
          assertEquals(1, context.getBeansOfType(StableDomainHostResolver.class).size());
          assertEquals(1, context.getBeansOfType(RuntimeGatewaySyncListener.class).size());
          assertEquals(Boolean.TRUE, context.getBean(DevRuntimeProperties.class).getGateway().getEnabled());
          assertEquals("apisix", context.getBean(DevRuntimeProperties.class).getGateway().getProvider());
          assertEquals("develop.example.com", context.getBean(DevRuntimeProperties.class).getGateway().getStableDomainHostSuffix());
        });
  }

  @Test
  void shouldLetUserProvidedGatewayBeansOverrideDefaults() {
    new ApplicationContextRunner()
        .withPropertyValues("ouroboros.dev.runtime.gateway.stable-domain-host-suffix=develop.example.com")
        .withUserConfiguration(GatewaySupportConfiguration.class)
        .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class))
        .run(context -> {
          assertEquals(1, context.getBeansOfType(RuntimeGatewaySyncListener.class).size());
          assertEquals(CustomGatewayRouteSyncPublisher.class, context.getBean(GatewayRouteSyncPublisher.class).getClass());
          assertEquals(CustomStableDomainHostResolver.class, context.getBean(StableDomainHostResolver.class).getClass());
        });
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApplication {
  }

  @Configuration
  static class OverrideStateStoreConfiguration {
    @Bean
    RuntimeStateStore customRuntimeStateStore() {
      return new CustomRuntimeStateStore();
    }
  }

  @Configuration
  static class GatewaySupportConfiguration {
    @Bean
    GatewayRouteSyncPublisher gatewayRouteSyncPublisher() {
      return new CustomGatewayRouteSyncPublisher();
    }

    @Bean
    StableDomainHostResolver stableDomainHostResolver() {
      return new CustomStableDomainHostResolver();
    }
  }

  static class CustomRuntimeStateStore extends InMemoryRuntimeStateStore {
  }

  static class CustomGatewayRouteSyncPublisher implements GatewayRouteSyncPublisher {
    @Override
    public io.vavr.control.Try<Void> publish(GatewayRouteSyncCommand command) {
      return io.vavr.control.Try.success(null);
    }
  }

  static class CustomStableDomainHostResolver implements StableDomainHostResolver {
    @Override
    public String resolve(String appName, String profile) {
      return "custom.example.com";
    }
  }
}
