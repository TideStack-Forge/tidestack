package com.ouroboros.dev.runtime.docker.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

import com.ouroboros.container.model.Container;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.docker.support.DockerRuntimeTestSupport;
import com.ouroboros.dev.runtime.docker.mapper.DockerRuntimeMapper;
import com.ouroboros.mq.MQProducer;
import com.ouroboros.util.Json;

class DockerTestAppRuntimeBackendTest {

  @Test
  void shouldDelegateLifecycleToContainerServiceAndExposePauseCapability() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerTestAppRuntimeBackend backend = new DockerTestAppRuntimeBackend(containerService);

    RuntimeInstance created = backend.create(DockerRuntimeTestSupport.testAppSpec()).get();
    RuntimeInstance started = backend.start(DockerRuntimeTestSupport.testAppSpec()).get();
    RuntimeInstance stopped = backend.stop(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER)).get();
    RuntimeInstance restarted = backend.restart(DockerRuntimeTestSupport.testAppSpec(), 7L).get();
    backend.delete(RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.DOCKER)).get();

    assertEquals(Arrays.asList("create", "start", "stop", "restart", "delete"), containerService.operationTrace());
    assertEquals(RuntimeStatus.STOPPED, created.getStatus());
    assertEquals(RuntimeStatus.RUNNING, started.getStatus());
    assertEquals(RuntimeStatus.STOPPED, stopped.getStatus());
    assertEquals(Long.valueOf(7L), restarted.getRuntimeGeneration());
    assertTrue(restarted.getCapabilities().isSupportsPause());
  }

  @Test
  void shouldSendRefreshCommandWhenReloadingWorkspace() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    CapturingProducer producer = new CapturingProducer();
    DockerTestAppRuntimeBackend backend = new DockerTestAppRuntimeBackend(containerService, producer);

    backend.create(DockerRuntimeTestSupport.testAppSpec()).get();
    backend.start(DockerRuntimeTestSupport.testAppSpec()).get();

    RuntimeInstance reloaded = backend.reloadWorkspace(DockerRuntimeTestSupport.testAppSpec(), 9L).get();

    assertEquals(Long.valueOf(9L), reloaded.getRuntimeGeneration());
    assertEquals(1, producer.messages.size());
    Map<String, Object> payload = Json.toMap(new String((byte[]) producer.messages.get(0).getPayload(), StandardCharsets.UTF_8));
    assertEquals("refresh", payload.get("command"));
    assertEquals("crm-alice", payload.get("appAndProfile"));
  }

  @Test
  void shouldPublishWebAndDebugPortsForTempPortSpec() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerTestAppRuntimeBackend backend = new DockerTestAppRuntimeBackend(containerService);

    backend.create(tempPortSpec()).get();

    Container created = containerService.getContainerInfoByName("ouroboros-test-app-crm-alice").get();
    Map<Integer, Integer> tcpBindings = created.getPortBindings().get("tcp");
    assertEquals(Integer.valueOf(32080), tcpBindings.get(Integer.valueOf(80)));
    assertEquals(Integer.valueOf(32082), tcpBindings.get(Integer.valueOf(5005)));
    assertEquals(2, tcpBindings.size());
    assertFalse(tcpBindings.containsKey(Integer.valueOf(8080)));
    assertEquals("32080", created.getLabels().get(DockerRuntimeMapper.WEB_PORT_LABEL));
    assertFalse(created.getLabels().containsKey(DockerRuntimeMapper.API_PORT_LABEL));
    assertEquals("32082", created.getLabels().get(DockerRuntimeMapper.API_DEBUG_PORT_LABEL));
  }

  @Test
  void shouldNotPublishHostPortsForStableDomainSpec() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    DockerTestAppRuntimeBackend backend = new DockerTestAppRuntimeBackend(containerService);

    backend.create(stableDomainSpec()).get();

    Container created = containerService.getContainerInfoByName("ouroboros-test-app-crm-alice").get();
    assertNull(created.getPortBindings());
    assertFalse(created.getLabels().containsKey(DockerRuntimeMapper.WEB_PORT_LABEL));
    assertFalse(created.getLabels().containsKey(DockerRuntimeMapper.API_PORT_LABEL));
    assertFalse(created.getLabels().containsKey(DockerRuntimeMapper.API_DEBUG_PORT_LABEL));
  }

  private static RuntimeSpec tempPortSpec() {
    return RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(com.ouroboros.dev.runtime.model.WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.TEMP_PORT)
        .requestedPorts(requestedPorts())
        .image("ouroboros/test-app:latest")
        .workspaceRef(com.ouroboros.dev.runtime.model.WorkspaceRef.builder()
            .workspaceMode(com.ouroboros.dev.runtime.model.WorkspaceMode.HOST_PATH)
            .hostPath("/workspace/run/crm/alice")
            .build())
        .build();
  }

  private static RuntimeSpec stableDomainSpec() {
    return RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(com.ouroboros.dev.runtime.model.WorkspaceMode.HOST_PATH)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .requestedPorts(requestedPorts())
        .image("ouroboros/test-app:latest")
        .workspaceRef(com.ouroboros.dev.runtime.model.WorkspaceRef.builder()
            .workspaceMode(com.ouroboros.dev.runtime.model.WorkspaceMode.HOST_PATH)
            .hostPath("/workspace/run/crm/alice")
            .build())
        .build();
  }

  private static Map<String, Integer> requestedPorts() {
    Map<String, Integer> ports = new LinkedHashMap<String, Integer>();
    ports.put("web", Integer.valueOf(32080));
    ports.put("api", Integer.valueOf(32081));
    ports.put("apiDebug", Integer.valueOf(32082));
    return ports;
  }

  private static class CapturingProducer implements MQProducer {
    private final List<Message<?>> messages = new ArrayList<Message<?>>();

    @Override
    public void send(Message<?> message) {
      messages.add(message);
    }

    @Override
    public void send(String routingKey, Message<?> message) {
      messages.add(message);
    }
  }
}
