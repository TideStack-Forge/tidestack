package com.ouroboros.dev.runtime.kubernetes;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationEventPublisher;

import io.fabric8.kubernetes.client.KubernetesClient;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.runtime.broker.BrokerPortAllocator;
import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.backend.KubernetesTestAppRuntimeBackend;
import com.ouroboros.dev.runtime.kubernetes.backend.KubernetesDevHelperRuntimeBackend;
import com.ouroboros.dev.runtime.kubernetes.debug.KubernetesDebugConnector;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeLeaseCleaner;
import com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeStatusSynchronizer;
import com.ouroboros.dev.runtime.kubernetes.tunnel.KubernetesTcpTunnelTransport;
import com.ouroboros.dev.runtime.kubernetes.workspace.PvcWorkspaceProvisioner;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.devhelper.dev.DevHelperCommandService;

@Configuration
@EnableConfigurationProperties(KubernetesRuntimeProperties.class)
public class KubernetesRuntimeAutoConfiguration {

  @Bean
  @ConditionalOnBean(KubernetesClient.class)
  public com.ouroboros.dev.runtime.kubernetes.backend.KubernetesLanguageRuntimeBackend kubernetesLanguageRuntimeBackend(
      KubernetesClient client, KubernetesRuntimeProperties properties) {
    return new com.ouroboros.dev.runtime.kubernetes.backend.KubernetesLanguageRuntimeBackend(client, properties);
  }

  @Bean
  public PvcWorkspaceProvisioner pvcWorkspaceProvisioner(
      DevelopWorkspaceContext developWorkspaceContext,
      KubernetesRuntimeProperties properties
  ) {
    return new PvcWorkspaceProvisioner(developWorkspaceContext, properties);
  }

  @Bean
  public KubernetesRuntimeResourceFactory kubernetesRuntimeResourceFactory(KubernetesRuntimeProperties properties) {
    return new KubernetesRuntimeResourceFactory(properties);
  }

  @Bean
  @ConditionalOnBean(KubernetesClient.class)
  public KubernetesRuntimeStatusSynchronizer kubernetesRuntimeStatusSynchronizer(
      KubernetesClient kubernetesClient,
      KubernetesRuntimeProperties properties,
      ObjectProvider<DevRuntimeEventPublisher> eventPublisherProvider
  ) {
    return new KubernetesRuntimeStatusSynchronizer(
        kubernetesClient,
        properties,
        eventPublisherProvider.getIfAvailable(DevRuntimeEventPublisher::noop)
    );
  }

  @Bean
  @ConditionalOnBean(KubernetesClient.class)
  public KubernetesDebugConnector kubernetesDebugConnector(
      KubernetesClient kubernetesClient,
      KubernetesRuntimeProperties properties
  ) {
    return new KubernetesDebugConnector(kubernetesClient, properties);
  }

  @Bean
  @ConditionalOnBean(KubernetesClient.class)
  public KubernetesRuntimeLeaseCleaner kubernetesRuntimeLeaseCleaner(
      KubernetesClient kubernetesClient,
      KubernetesRuntimeProperties properties
  ) {
    return new KubernetesRuntimeLeaseCleaner(kubernetesClient, properties);
  }

  @Bean
  @ConditionalOnBean(KubernetesClient.class)
  public KubernetesTestAppRuntimeBackend kubernetesTestAppRuntimeBackend(
      KubernetesClient kubernetesClient,
      KubernetesRuntimeResourceFactory resourceFactory,
      KubernetesRuntimeStatusSynchronizer statusSynchronizer,
      KubernetesRuntimeProperties properties
  ) {
    return new KubernetesTestAppRuntimeBackend(kubernetesClient, resourceFactory, statusSynchronizer, properties);
  }

  @Bean
  @ConditionalOnBean({KubernetesClient.class, DevHelperCommandService.class})
  public KubernetesDevHelperRuntimeBackend kubernetesDevHelperRuntimeBackend(
      KubernetesClient kubernetesClient,
      KubernetesRuntimeResourceFactory resourceFactory,
      KubernetesRuntimeProperties properties,
      DevHelperCommandService devHelperCommandService,
      KubernetesRuntimeStatusSynchronizer statusSynchronizer,
      DevRuntimeEventPublisher devRuntimeEventPublisher,
      ApplicationEventPublisher applicationEventPublisher
  ) {
    return new KubernetesDevHelperRuntimeBackend(
        kubernetesClient,
        resourceFactory,
        properties,
        devHelperCommandService,
        statusSynchronizer,
        devRuntimeEventPublisher,
        null,
        applicationEventPublisher
    );
  }

  @Bean
  @ConditionalOnBean(BrokerPortAllocator.class)
  public KubernetesTcpTunnelTransport kubernetesTcpTunnelTransport(
      BrokerPortAllocator portAllocator,
      DevRuntimeProperties devRuntimeProperties
  ) {
    return new KubernetesTcpTunnelTransport(portAllocator, devRuntimeProperties.getDebug().getBrokerHost());
  }
}
