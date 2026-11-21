package com.ouroboros.dev.runtime;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.SpringDevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.gateway.RuntimeGatewayAutoConfiguration;
import com.ouroboros.dev.runtime.service.DefaultDevRuntimeService;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.DebugSessionStore;
import com.ouroboros.dev.runtime.service.InMemoryDebugSessionStore;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeLockStore;
import com.ouroboros.dev.runtime.service.InMemoryRuntimeStateStore;
import com.ouroboros.dev.runtime.service.DefaultRuntimeLockService;
import com.ouroboros.dev.runtime.service.RuntimeBackend;
import com.ouroboros.dev.runtime.service.RuntimeBackendResolver;
import com.ouroboros.dev.runtime.service.RuntimeLockService;
import com.ouroboros.dev.runtime.service.RuntimeLockStore;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.dev.runtime.service.WorkspaceProvisioner;

@Configuration
@EnableConfigurationProperties(DevRuntimeProperties.class)
@Import(RuntimeGatewayAutoConfiguration.class)
public class DevRuntimeCoreAutoConfiguration {

  @Bean
  public RuntimeBackendResolver runtimeBackendResolver(List<RuntimeBackend> runtimeBackends, DevRuntimeProperties properties) {
    return new RuntimeBackendResolver(runtimeBackends, properties);
  }

  @Bean
  public DevRuntimeEventPublisher devRuntimeEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    return new SpringDevRuntimeEventPublisher(applicationEventPublisher);
  }

  @Bean
  @ConditionalOnMissingBean(RuntimeStateStore.class)
  public RuntimeStateStore runtimeStateStore() {
    return new InMemoryRuntimeStateStore();
  }

  @Bean
  @ConditionalOnMissingBean(RuntimeLockStore.class)
  public RuntimeLockStore runtimeLockStore() {
    return new InMemoryRuntimeLockStore();
  }

  @Bean
  @ConditionalOnMissingBean(RuntimeLockService.class)
  public RuntimeLockService runtimeLockService(RuntimeLockStore runtimeLockStore) {
    return new DefaultRuntimeLockService(runtimeLockStore);
  }

  @Bean
  @ConditionalOnMissingBean(DebugSessionStore.class)
  public DebugSessionStore debugSessionStore() {
    return new InMemoryDebugSessionStore();
  }

  @Bean
  public DevRuntimeService devRuntimeService(
      List<RuntimeBackend> runtimeBackends,
      List<WorkspaceProvisioner> workspaceProvisioners,
      RuntimeStateStore runtimeStateStore,
      DevRuntimeProperties properties,
      DevRuntimeEventPublisher eventPublisher
  ) {
    return new DefaultDevRuntimeService(runtimeBackends, workspaceProvisioners, runtimeStateStore, properties, eventPublisher);
  }
}
