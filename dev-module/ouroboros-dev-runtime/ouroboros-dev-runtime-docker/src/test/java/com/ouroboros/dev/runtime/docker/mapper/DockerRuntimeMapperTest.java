package com.ouroboros.dev.runtime.docker.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.container.ContainerStatus;
import com.ouroboros.container.model.Container;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;

class DockerRuntimeMapperTest {

  @Test
  void shouldMapContainerLabelsToStableEndpoints() {
    Container container = new Container();
    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put("OUROBOROS_APP_NAME", "crm");
    labels.put("OUROBOROS_APP_PROFILE", "alice");
    labels.put("ouroboros-test-app", "CRM");
    labels.put("WEB_PORT", "30080");
    labels.put("API_PORT", "30081");
    labels.put("API_DEBUG_PORT", "35005");
    container.setId("cid-1");
    container.setName("ouroboros-test-app-crm-alice");
    container.setStatus(ContainerStatus.RUNNING);
    container.setLabels(labels);
    container.setPortBindings(Collections.singletonMap("tcp", portBindings()));

    RuntimeInstance runtime = DockerRuntimeMapper.toRuntime(container, RuntimeKind.TEST_APP);

    assertEquals("cid-1", runtime.getRuntimeId());
    assertEquals("crm", runtime.getAppName());
    assertEquals("alice", runtime.getProfile());
    assertEquals(RuntimeStatus.RUNNING, runtime.getStatus());
    assertEquals(BackendType.DOCKER, runtime.getBackendType());
    assertEquals("http://127.0.0.1:30080", runtime.getWebEndpoint());
    assertEquals("http://127.0.0.1:30081", runtime.getApiEndpoint());
    assertEquals(null, runtime.getDebugEndpoint());
    assertEquals("30080", runtime.getAccessRef().get("webPort"));
    assertEquals("30081", runtime.getAccessRef().get("apiPort"));
    assertEquals("35005", runtime.getAccessRef().get("debugPort"));
    assertTrue(runtime.getCapabilities().isSupportsPause());
    assertTrue(runtime.getCapabilities().isSupportsDebugPort());
    assertTrue(runtime.getCapabilities().isSupportsDebugSession());
  }

  private Map<Integer, Integer> portBindings() {
    Map<Integer, Integer> ports = new LinkedHashMap<Integer, Integer>();
    ports.put(80, 30080);
    ports.put(8080, 30081);
    ports.put(5005, 35005);
    return ports;
  }
}
