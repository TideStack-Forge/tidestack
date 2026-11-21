package com.ouroboros.dev.runtime.docker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.SpringFactoriesLoader;

import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.runtime.docker.backend.DockerDevHelperRuntimeBackend;
import com.ouroboros.dev.runtime.docker.backend.DockerTestAppRuntimeBackend;
import com.ouroboros.dev.runtime.docker.debug.DockerDebugConnector;
import com.ouroboros.dev.runtime.docker.support.DockerRuntimeTestSupport;
import com.ouroboros.dev.runtime.docker.event.DockerRuntimeEventBridge;
import com.ouroboros.dev.runtime.docker.workspace.HostPathWorkspaceProvisioner;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.model.WorkspaceRef;
import com.ouroboros.dev.runtime.service.DebugConnector;
import com.ouroboros.dev.runtime.service.RuntimeBackend;
import com.ouroboros.dev.runtime.service.WorkspaceProvisioner;
import com.ouroboros.mq.MQBridge;

class DockerRuntimeAutoConfigurationTest {

  @Test
  void shouldDiscoverAutoConfigurationThroughSpringFactories() {
    List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(
        EnableAutoConfiguration.class,
        Thread.currentThread().getContextClassLoader()
    );

    assertTrue(factoryNames.contains(DockerRuntimeAutoConfiguration.class.getName()));
  }

  @Test
  void shouldContributeDockerBackendsAndHostPathProvisioner() {
    new ApplicationContextRunner()
        .withBean(ContainerService.class, DockerRuntimeTestSupport.FakeContainerService::new)
        .withBean(DevelopWorkspaceContext.class, DockerRuntimeTestSupport.FakeDevelopWorkspaceContext::new)
        .withBean(DevRuntimeEventPublisher.class, DockerRuntimeTestSupport.RecordingDevRuntimeEventPublisher::new)
        .withBean(MQBridge.class, DockerRuntimeAutoConfigurationTest::mqBridge)
        .withConfiguration(AutoConfigurations.of(DockerRuntimeAutoConfiguration.class))
        .run(context -> {
          assertTrue(context.containsBean("dockerRuntimeEventBridge"));
          assertTrue(context.containsBean("hostPathWorkspaceProvisioner"));
          assertEquals(3, context.getBeansOfType(RuntimeBackend.class).size());
          assertEquals(com.ouroboros.dev.runtime.model.RuntimeKind.LANGUAGE_SERVICE, context.getBean(com.ouroboros.dev.runtime.docker.backend.DockerLanguageRuntimeBackend.class).getRuntimeKind());
          assertEquals(1, context.getBeansOfType(HostPathWorkspaceProvisioner.class).size());
          assertEquals(1, context.getBeansOfType(DockerRuntimeEventBridge.class).size());
          assertEquals(1, context.getBeansOfType(DockerTestAppRuntimeBackend.class).size());
          assertEquals(1, context.getBeansOfType(DockerDevHelperRuntimeBackend.class).size());
          assertEquals(1, context.getBeansOfType(DebugConnector.class).size());
          assertEquals(1, context.getBeansOfType(DockerDebugConnector.class).size());
          assertEquals(1, context.getBeansOfType(WorkspaceProvisioner.class).size());
        });
  }

  @Test
  void shouldPreferHostWorkspacePathForDockerHostPathProvisioning() {
    new ApplicationContextRunner()
        .withPropertyValues("HOST_WORKSPACE_PATH=/host/workspace")
        .withBean(ContainerService.class, DockerRuntimeTestSupport.FakeContainerService::new)
        .withBean(DevelopWorkspaceContext.class, DockerRuntimeTestSupport.FakeDevelopWorkspaceContext::new)
        .withBean(DevRuntimeEventPublisher.class, DockerRuntimeTestSupport.RecordingDevRuntimeEventPublisher::new)
        .withBean(MQBridge.class, DockerRuntimeAutoConfigurationTest::mqBridge)
        .withConfiguration(AutoConfigurations.of(DockerRuntimeAutoConfiguration.class))
        .run(context -> {
          HostPathWorkspaceProvisioner provisioner = context.getBean(HostPathWorkspaceProvisioner.class);

          WorkspaceRef workspaceRef = provisioner.prepare(
              DockerRuntimeTestSupport.helperSpec(),
              WorkspaceProvisioner.Operation.CREATE
          ).get();

          assertEquals("/host/workspace/run/crm/dev-helper", workspaceRef.getHostPath());
        });
  }

  private static MQBridge mqBridge() {
    return org.mockito.Mockito.mock(MQBridge.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
  }
}
