package com.ouroboros.dev.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.service.impl.AppContainerServiceImpl;
import com.ouroboros.dev.app.service.support.KubernetesAppRuntimeServiceIntegrationSupport;

class AppContainerServiceKubernetesIntegrationTest {

  @TempDir
  Path tempDir;

  private KubernetesAppRuntimeServiceIntegrationSupport.TestAppEnvironment environment;
  private AppContainerServiceImpl service;

  @BeforeEach
  void setUp() {
    environment = KubernetesAppRuntimeServiceIntegrationSupport.testAppEnvironment(tempDir);
    service = environment.service();
  }

  @AfterEach
  void tearDown() {
    environment.close();
  }

  @Test
  void shouldDriveKubernetesTestAppRuntimeThroughServiceApi() {
    AppContainerRequest request = environment.request();

    String runtimeId = service.create(request, "alice").get();

    Map<String, Object> launching = service.getStatus(runtimeId).get();
    assertEquals(ContainerStatus.LAUNCHING, launching.get("status"));

    environment.markReady();
    Map<String, Object> running = service.getStatus(runtimeId).get();
    assertEquals(ContainerStatus.RUNNING, running.get("status"));
    assertEquals("KUBERNETES", running.get("backendType"));
    assertEquals("TEMP_PORT", running.get("exposureStrategy"));
    assertTrue(String.valueOf(running.get("webEndpoint")).startsWith("http://127.0.0.1:"));
    assertEquals(null, running.get("apiEndpoint"));
    assertEquals("{\"version\":1}", environment.readMountedMetadata("app.json"));

    environment.writeDevMetadata("app.json", "{\"version\":2}");
    environment.deleteReadyPod();
    service.reloadWorkspace(runtimeId).get();

    Map<String, Object> reloading = service.getStatus(runtimeId).get();
    assertEquals(ContainerStatus.LAUNCHING, reloading.get("status"));
    assertEquals(Long.valueOf(1L), reloading.get("runtimeGeneration"));
    assertEquals("{\"version\":2}", environment.readMountedMetadata("app.json"));

    environment.markReady();
    Map<String, Object> runningAfterReload = service.getStatus(runtimeId).get();
    assertEquals(ContainerStatus.RUNNING, runningAfterReload.get("status"));
    assertEquals(Long.valueOf(1L), runningAfterReload.get("runtimeGeneration"));

    service.delete(runtimeId).get();

    Map<String, Object> deleted = service.getStatus("crm", "alice").get();
    assertEquals(ContainerStatus.NOT_FOUND, deleted.get("status"));
  }
}
