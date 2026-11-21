package com.ouroboros.dev.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.dev.app.model.AppDevHelperRequest;
import com.ouroboros.dev.app.service.impl.AppDevHelperContainerServiceImpl;
import com.ouroboros.dev.app.service.support.KubernetesAppRuntimeServiceIntegrationSupport;

class AppDevHelperContainerServiceKubernetesIntegrationTest {

  @TempDir
  Path tempDir;

  private KubernetesAppRuntimeServiceIntegrationSupport.DevHelperEnvironment environment;
  private AppDevHelperContainerServiceImpl service;

  @BeforeEach
  void setUp() {
    environment = KubernetesAppRuntimeServiceIntegrationSupport.devHelperEnvironment(tempDir);
    service = environment.service();
  }

  @AfterEach
  void tearDown() {
    environment.close();
  }

  @Test
  void shouldDriveKubernetesDevHelperRuntimeThroughServiceApi() {
    AppDevHelperRequest request = environment.request();
    environment.writeDevelopSupportFile("vcs/crm/credentials.json", "secret-token");

    String runtimeId = service.createAndStart(request).get();

    Map<String, Object> launching = service.getStatus("crm").get();
    assertEquals(ContainerStatus.LAUNCHING, launching.get("status"));
    assertEquals(runtimeId, launching.get("containerId"));
    environment.emitHelperOnline("helper-a", 1000L);
    assertEquals(Long.valueOf(1L), environment.latestRequestGeneration());

    environment.emitRefreshSuccess(1L);
    Map<String, Object> running = service.getStatus("crm").get();
    assertEquals(ContainerStatus.RUNNING, running.get("status"));
    assertNotNull(running.get("lastLoadTime"));
    assertEquals("seed-v1", environment.readMountedDevelopSupport("helper.json"));
    assertFalse(environment.mountedDevelopSupportExists("vcs/crm/credentials.json"));

    environment.writeDevelopSupportFile("helper.json", "seed-v2");
    service.reload("crm").get();

    Map<String, Object> reloading = service.getStatus("crm").get();
    assertEquals(ContainerStatus.LAUNCHING, reloading.get("status"));
    environment.emitHelperOnline("helper-b", 2000L);
    assertEquals(Long.valueOf(2L), environment.latestRequestGeneration());
    assertEquals("seed-v2", environment.readMountedDevelopSupport("helper.json"));
    assertFalse(environment.mountedDevelopSupportExists("vcs/crm/credentials.json"));

    environment.emitRefreshSuccess(2L);
    Map<String, Object> runningAfterReload = service.getStatus("crm").get();
    assertEquals(ContainerStatus.RUNNING, runningAfterReload.get("status"));
    assertNotNull(runningAfterReload.get("lastLoadTime"));

    service.delete("crm").get();

    Map<String, Object> deleted = service.getStatus("crm").get();
    assertEquals(ContainerStatus.NOT_FOUND, deleted.get("status"));
  }

  @Test
  void shouldRetryKubernetesRefreshAfterSendFailure() {
    service.createAndStart(environment.request()).get();
    environment.failNextRefreshSend(new IllegalStateException("mq unavailable"));

    environment.emitHelperOnline("helper-a", 1000L);

    assertEquals(Long.valueOf(1L), environment.latestRequestGeneration());
    assertEquals(2, environment.refreshRequestCount());
    Map<String, Object> retrying = service.getStatus("crm").get();
    assertEquals(ContainerStatus.LAUNCHING, retrying.get("status"));

    environment.emitRefreshSuccess(1L);

    Map<String, Object> running = service.getStatus("crm").get();
    assertEquals(ContainerStatus.RUNNING, running.get("status"));
    assertNotNull(running.get("lastLoadTime"));
  }

  @Test
  void shouldStayFailedAfterTwoKubernetesRefreshSendFailuresAndLateSuccess() {
    service.createAndStart(environment.request()).get();
    environment.failNextRefreshSend(new IllegalStateException("mq unavailable"));
    environment.failNextRefreshSend(new IllegalStateException("mq still unavailable"));

    environment.emitHelperOnline("helper-a", 1000L);

    assertEquals(2, environment.refreshRequestCount());
    Map<String, Object> failed = service.getStatus("crm").get();
    assertEquals(ContainerStatus.UNKNOWN, failed.get("status"));
    assertNull(failed.get("lastLoadTime"));

    environment.emitRefreshSuccess(1L);

    Map<String, Object> afterLateSuccess = service.getStatus("crm").get();
    assertEquals(ContainerStatus.UNKNOWN, afterLateSuccess.get("status"));
    assertNull(afterLateSuccess.get("lastLoadTime"));
  }
}
