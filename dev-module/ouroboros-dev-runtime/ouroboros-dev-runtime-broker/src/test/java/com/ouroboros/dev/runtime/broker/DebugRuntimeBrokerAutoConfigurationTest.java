package com.ouroboros.dev.runtime.broker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.DevRuntimeCoreAutoConfiguration;
import com.ouroboros.dev.runtime.gateway.RuntimeGatewayCoordinator;
import com.ouroboros.dev.runtime.gateway.RuntimeGatewayRoute;
import com.ouroboros.dev.runtime.gateway.RuntimeGatewayRouteKey;
import com.ouroboros.dev.runtime.gateway.RuntimeGatewaySyncListener;

class DebugRuntimeBrokerAutoConfigurationTest {

  @Test
  void shouldProvideDebugSessionRuntimeEventListenerWithoutGatewayCoordinator() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, DebugRuntimeBrokerAutoConfiguration.class))
        .run(context -> assertEquals(1, context.getBeansOfType(DebugSessionRuntimeEventListener.class).size()));
  }

  @Test
  void shouldKeepDebugSessionRuntimeEventListenerWhenGatewaySyncListenerIsPresent() {
    new ApplicationContextRunner()
        .withUserConfiguration(GatewayCoordinatorConfiguration.class)
        .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, DebugRuntimeBrokerAutoConfiguration.class))
        .run(context -> {
          assertEquals(1, context.getBeansOfType(DebugSessionRuntimeEventListener.class).size());
          assertEquals(1, context.getBeansOfType(RuntimeGatewaySyncListener.class).size());
        });
  }

  @Test
  void shouldForceLoopbackOnlyBrokerBindingEvenWhenUnsafeBindScopeConfigured() {
    new ApplicationContextRunner()
        .withPropertyValues(
            "ouroboros.dev.runtime.debug.bind-scope=INTERNAL_NETWORK",
            "ouroboros.dev.runtime.debug.loopback-only=false"
        )
        .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, DebugRuntimeBrokerAutoConfiguration.class))
        .run(context -> {
          TcpTunnelTransport transport = context.getBean(TcpTunnelTransport.class);
          java.lang.reflect.Field bindScopeField = TcpTunnelTransport.class.getDeclaredField("bindScope");
          bindScopeField.setAccessible(true);
          assertEquals("LOOPBACK_ONLY", String.valueOf(bindScopeField.get(transport)));
        });
  }

  @Configuration
  static class GatewayCoordinatorConfiguration {
    @Bean
    RuntimeGatewayCoordinator runtimeGatewayCoordinator() {
      return new RuntimeGatewayCoordinator() {
        @Override
        public Try<Void> upsert(RuntimeGatewayRoute route) {
          return Try.success(null);
        }

        @Override
        public Try<Void> delete(RuntimeGatewayRouteKey key) {
          return Try.success(null);
        }
      };
    }
  }
}
