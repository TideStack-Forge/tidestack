package com.ouroboros.dev.runtime.kubernetes.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.vavr.control.Either;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.runtime.docker.backend.DockerDevHelperRuntimeBackend;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeStatusSynchronizer;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesMockServerSupport;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesRuntimeTestSupport;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.model.WorkspaceRef;
import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.metadata.HelperRuntimeActivatedEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataRefreshRequestedEvent;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;
import com.ouroboros.exception.PlatformException;

class HelperRuntimeBackendEventIsolationTest {
  @TempDir
  Path workspace;

  @Test
  void springEventsShouldRemainBoundToTheirRuntimeBackend() {
    try (KubernetesMockServerSupport server = KubernetesMockServerSupport.crudHttps();
         AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      server.start();
      List<DevRuntimeEvent> dockerEvents = new CopyOnWriteArrayList<DevRuntimeEvent>();
      DockerDevHelperRuntimeBackend dockerBackend = new DockerDevHelperRuntimeBackend(
          inMemoryContainerService(),
          dockerEvents::add,
          context
      );
      KubernetesRuntimeProperties properties = KubernetesRuntimeTestSupport.properties();
      KubernetesRuntimeTestSupport.RecordingDevHelperCommandService commandService =
          new KubernetesRuntimeTestSupport.RecordingDevHelperCommandService();
      KubernetesDevHelperRuntimeBackend kubernetesBackend = new KubernetesDevHelperRuntimeBackend(
          server.getClient(),
          new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
          properties,
          commandService,
          new KubernetesRuntimeStatusSynchronizer(server.getClient(), properties, null),
          event -> {
          },
          null,
          context
      );
      context.registerBean(DockerDevHelperRuntimeBackend.class, () -> dockerBackend);
      context.registerBean(KubernetesDevHelperRuntimeBackend.class, () -> kubernetesBackend);
      context.refresh();

      RuntimeSpec dockerSpec = dockerSpec(workspace);
      RuntimeInstance dockerRuntime = dockerBackend.create(dockerSpec).flatMap(ignored -> dockerBackend.start(dockerSpec)).get();
      kubernetesBackend.start(KubernetesRuntimeTestSupport.helperSpec()).get();
      HelperRuntimeTarget dockerTarget = new HelperRuntimeTarget(
          BackendType.DOCKER.name(),
          "dev-helper",
          dockerRuntime.getRuntimeId()
      );
      HelperIdentity dockerHelper = new HelperIdentity("docker-helper", 1000L, "app-incarnation");

      context.publishEvent(new HelperRuntimeActivatedEvent("crm", dockerHelper, dockerTarget));
      context.publishEvent(new MetadataRefreshRequestedEvent(
          "crm",
          dockerRuntime.getRuntimeGeneration(),
          dockerHelper,
          "docker-refresh",
          dockerTarget
      ));
      context.publishEvent(new MetadataSnapshotPublishedEvent(
          "crm",
          dockerRuntime.getRuntimeGeneration(),
          dockerHelper.getInstanceId(),
          dockerHelper.getStartedAtEpochMillis(),
          dockerHelper.getAppIncarnationId(),
          "docker-refresh",
          1L,
          dockerTarget
      ));

      RuntimeIdentityKey dockerKey = RuntimeIdentityKey.of(
          "crm",
          "dev-helper",
          RuntimeKind.DEV_HELPER,
          BackendType.DOCKER
      );
      RuntimeIdentityKey kubernetesKey = RuntimeIdentityKey.from(KubernetesRuntimeTestSupport.helperSpec());
      assertEquals(RuntimeStatus.RUNNING, dockerBackend.getStatus(dockerKey).get().getStatus());
      assertEquals(RuntimeStatus.STARTING, kubernetesBackend.getStatus(kubernetesKey).get().getStatus());
      assertTrue(commandService.requests().isEmpty());

      context.publishEvent(KubernetesRuntimeTestSupport.helperActivated("crm", "kubernetes-helper", 2000L));
      CommandRequest kubernetesRequest = commandService.requests().get(0);
      context.publishEvent(KubernetesRuntimeTestSupport.publishedSnapshot(kubernetesRequest));

      assertEquals(RuntimeStatus.RUNNING, kubernetesBackend.getStatus(kubernetesKey).get().getStatus());
      assertEquals(RuntimeStatus.RUNNING, dockerBackend.getStatus(dockerKey).get().getStatus());
      assertEquals(1, dockerEvents.size());
    }
  }

  private static RuntimeSpec dockerSpec(Path workspace) {
    return RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .env(Collections.singletonMap("OUROBOROS_APP_INCARNATION_ID", "app-incarnation"))
        .workspaceRef(WorkspaceRef.builder()
            .workspaceMode(WorkspaceMode.HOST_PATH)
            .hostPath(workspace.toString())
            .build())
        .build();
  }

  private static ContainerService inMemoryContainerService() {
    AtomicReference<Container> currentContainer = new AtomicReference<Container>();
    return (ContainerService) Proxy.newProxyInstance(
        ContainerService.class.getClassLoader(),
        new Class<?>[]{ContainerService.class},
        (proxy, method, args) -> {
          String methodName = method.getName();
          if ("create".equals(methodName)) {
            Container container = (Container) args[0];
            container.setId("docker-runtime");
            container.setStatus(ContainerStatus.STOPPED);
            currentContainer.set(container);
            return Either.right(container.getId());
          }
          if ("start".equals(methodName)) {
            currentContainer.get().setStatus(ContainerStatus.RUNNING);
            return Either.right(null);
          }
          if ("getContainerInfoByName".equals(methodName)) {
            Container container = currentContainer.get();
            if (container == null) {
              return Either.left(
                  new PlatformException(
                      "docker error",
                      new IllegalStateException("No such container")));
            }
            return Either.right(container);
          }
          if ("getLogs".equals(methodName)) {
            return Either.right(Collections.emptyList());
          }
          if ("pullImageAsync".equals(methodName)) {
            return CompletableFuture.completedFuture(null);
          }
          if ("registerEventListener".equals(methodName)) {
            return null;
          }
          if ("toString".equals(methodName)) {
            return "in-memory-container-service";
          }
          throw new UnsupportedOperationException("Unexpected container operation: " + methodName);
        }
    );
  }
}
