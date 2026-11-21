package com.ouroboros.dev.runtime.docker;

import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationEventPublisher;

import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.runtime.broker.BrokerPortAllocator;
import com.ouroboros.dev.runtime.config.DevRuntimeProperties;
import com.ouroboros.dev.runtime.docker.backend.DockerDevHelperRuntimeBackend;
import com.ouroboros.dev.runtime.docker.backend.DockerTestAppRuntimeBackend;
import com.ouroboros.dev.runtime.docker.debug.DockerDebugConnector;
import com.ouroboros.dev.runtime.docker.debug.DockerDebugTargetResolver;
import com.ouroboros.dev.runtime.docker.event.DockerRuntimeEventBridge;
import com.ouroboros.dev.runtime.docker.tunnel.DockerTcpTunnelTransport;
import com.ouroboros.dev.runtime.docker.workspace.HostPathWorkspaceProvisioner;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.mq.MQProducer;

@Configuration
public class DockerRuntimeAutoConfiguration {

  @Value("${HOST_WORKSPACE_PATH:}")
  private String hostWorkspacePath;

  @Bean
  public com.ouroboros.dev.runtime.docker.backend.DockerLanguageRuntimeBackend dockerLanguageRuntimeBackend(ContainerService containers) {
    return new com.ouroboros.dev.runtime.docker.backend.DockerLanguageRuntimeBackend(containers);
  }

  @Bean
  public DockerTestAppRuntimeBackend dockerTestAppRuntimeBackend(ContainerService containerService, MQBridge mqBridge) {
    MQProducer appControlProducer = mqBridge.bindingProducer("ouroboros-app-control-producer")
        .destination("ouroboros.app.control")
        .extraProp("declareExchange", true)
        .build();
    return new DockerTestAppRuntimeBackend(containerService, appControlProducer);
  }

  @Bean
  public DockerDevHelperRuntimeBackend dockerDevHelperRuntimeBackend(
      ContainerService containerService,
      DevRuntimeEventPublisher devRuntimeEventPublisher,
      ApplicationEventPublisher applicationEventPublisher
  ) {
    return new DockerDevHelperRuntimeBackend(
        containerService,
        devRuntimeEventPublisher,
        applicationEventPublisher
    );
  }

  @Bean
  public DockerDebugTargetResolver dockerDebugTargetResolver(ContainerService containerService) {
    return new DockerDebugTargetResolver(containerService);
  }

  @Bean
  public DockerDebugConnector dockerDebugConnector(DockerDebugTargetResolver dockerDebugTargetResolver) {
    return new DockerDebugConnector(dockerDebugTargetResolver);
  }

  @Bean
  @ConditionalOnBean(BrokerPortAllocator.class)
  public DockerTcpTunnelTransport dockerTcpTunnelTransport(
      ContainerService containerService,
      DockerDebugTargetResolver dockerDebugTargetResolver,
      BrokerPortAllocator brokerPortAllocator,
      DevRuntimeProperties properties
  ) {
    return new DockerTcpTunnelTransport(
        containerService,
        dockerDebugTargetResolver,
        brokerPortAllocator,
        properties.getDebug().getBrokerHost()
    );
  }

  @Bean
  public HostPathWorkspaceProvisioner hostPathWorkspaceProvisioner(DevelopWorkspaceContext developWorkspaceContext) {
    String hostRunRootDir = hostWorkspacePath == null || hostWorkspacePath.trim().isEmpty()
        ? developWorkspaceContext.getRunRootDir()
        : Paths.get(hostWorkspacePath, "run").toString();
    return new HostPathWorkspaceProvisioner(hostRunRootDir);
  }

  @Bean
  public DockerRuntimeEventBridge dockerRuntimeEventBridge(
      ContainerService containerService,
      DevRuntimeEventPublisher devRuntimeEventPublisher
  ) {
    DockerRuntimeEventBridge bridge = new DockerRuntimeEventBridge(devRuntimeEventPublisher);
    containerService.registerEventListener(bridge);
    return bridge;
  }
}
