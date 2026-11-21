package com.ouroboros.dev.runtime.broker;

import java.time.Clock;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.service.DebugConnector;
import com.ouroboros.dev.runtime.service.DebugSessionService;
import com.ouroboros.dev.runtime.service.DebugSessionStore;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.dev.runtime.service.TunnelBroker;
import com.ouroboros.dev.runtime.service.TunnelTransport;
import com.ouroboros.dev.runtime.transport.HttpTunnelTransport;

@Configuration
public class DebugRuntimeBrokerAutoConfiguration {
  private static final int DEFAULT_MIN_PORT = 43000;
  private static final int DEFAULT_MAX_PORT = 43999;

  @Bean
  @ConditionalOnMissingBean(BrokerPortAllocator.class)
  public BrokerPortAllocator brokerPortAllocator() {
    return new BrokerPortAllocator(DEFAULT_MIN_PORT, DEFAULT_MAX_PORT);
  }

  @Bean
  @ConditionalOnMissingBean(TcpTunnelTransport.class)
  public TcpTunnelTransport tcpTunnelTransport(BrokerPortAllocator brokerPortAllocator, DevRuntimeProperties properties) {
    return new TcpTunnelTransport(
        brokerPortAllocator,
        properties.getDebug().getBrokerHost(),
        com.ouroboros.dev.runtime.model.DebugSessionBindingScope.LOOPBACK_ONLY
    );
  }

  @Bean
  @ConditionalOnMissingBean(HttpTunnelTransport.class)
  public HttpTunnelTransport httpTunnelTransport() {
    return new HttpTunnelTransport("/api/tunnel");
  }

  @Bean
  @ConditionalOnMissingBean(TunnelBroker.class)
  public TunnelBroker tunnelBroker(List<TunnelTransport> tunnelTransports) {
    return new DefaultTunnelBroker(tunnelTransports);
  }

  @Bean
  @ConditionalOnMissingBean(DebugSessionService.class)
  public DebugSessionService debugSessionService(
      RuntimeStateStore runtimeStateStore,
      DevRuntimeService devRuntimeService,
      DebugSessionStore debugSessionStore,
      List<DebugConnector> debugConnectors,
      TunnelBroker tunnelBroker,
      DevRuntimeProperties properties
  ) {
    return new DefaultDebugSessionService(
        runtimeStateStore,
        devRuntimeService,
        debugSessionStore,
        debugConnectors,
        tunnelBroker,
        properties,
        Clock.systemUTC()
    );
  }

  @Bean
  @ConditionalOnMissingBean(DebugSessionReaper.class)
  public DebugSessionReaper debugSessionReaper(DebugSessionStore debugSessionStore, TunnelBroker tunnelBroker) {
    return new DebugSessionReaper(debugSessionStore, tunnelBroker);
  }

  @Bean
  @ConditionalOnMissingBean(DebugSessionRuntimeEventListener.class)
  public DebugSessionRuntimeEventListener debugSessionRuntimeEventListener(
      DebugSessionStore debugSessionStore,
      TunnelBroker tunnelBroker
  ) {
    return new DebugSessionRuntimeEventListener(debugSessionStore, tunnelBroker);
  }
}
