package com.ouroboros.dev.runtime.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncPublisher;
import com.ouroboros.dev.runtime.gateway.command.SpringGatewayRouteSyncPublisher;
import com.ouroboros.dev.runtime.gateway.host.DefaultStableDomainHostResolver;
import com.ouroboros.dev.runtime.gateway.host.StableDomainHostResolver;

@Configuration
public class RuntimeGatewayAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(GatewayRouteSyncPublisher.class)
  public GatewayRouteSyncPublisher gatewayRouteSyncPublisher(ApplicationEventPublisher applicationEventPublisher) {
    return new SpringGatewayRouteSyncPublisher(applicationEventPublisher);
  }

  @Bean
  @ConditionalOnMissingBean(StableDomainHostResolver.class)
  public StableDomainHostResolver stableDomainHostResolver(DevRuntimeProperties properties) {
    return new DefaultStableDomainHostResolver(properties.getGateway().getStableDomainHostSuffix());
  }

  @Bean
  @ConditionalOnMissingBean(RuntimeGatewaySyncListener.class)
  public RuntimeGatewaySyncListener runtimeGatewaySyncListener(
      GatewayRouteSyncPublisher gatewayRouteSyncPublisher,
      StableDomainHostResolver stableDomainHostResolver
  ) {
    return new RuntimeGatewaySyncListener(gatewayRouteSyncPublisher, stableDomainHostResolver);
  }
}
