package com.ouroboros.dev.runtime.docker.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.container.ContainerEventType;
import com.ouroboros.container.ContainerStatus;
import com.ouroboros.container.SimpleEventObject;
import com.ouroboros.dev.runtime.docker.mapper.DockerRuntimeMapper;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.docker.support.DockerRuntimeTestSupport.RecordingDevRuntimeEventPublisher;

class DockerRuntimeEventBridgeTest {

  @Test
  void shouldConvertDockerCallbacksIntoRuntimeEvents() {
    RecordingDevRuntimeEventPublisher publisher = new RecordingDevRuntimeEventPublisher();
    DockerRuntimeEventBridge bridge = new DockerRuntimeEventBridge(publisher);

    Map<String, Object> attributes = new LinkedHashMap<String, Object>();
    attributes.put("ouroboros-test-app", "CRM");
    attributes.put("OUROBOROS_APP_NAME", "crm");
    attributes.put("OUROBOROS_APP_PROFILE", "alice");
    attributes.put("WEB_PORT", "30080");

    bridge.onEvent(
        ContainerEventType.CONTAINER_STATUS_CHANGE,
        new SimpleEventObject("cid-1", ContainerStatus.RUNNING, attributes)
    );

    DevRuntimeEvent event = publisher.events().get(0);
    assertEquals(DevRuntimeEventType.RUNTIME_STATUS_CHANGED, event.getType());
    assertEquals("cid-1", event.getRuntimeId());
    assertEquals("crm", event.getAppName());
    assertEquals("alice", event.getProfile());
    assertEquals(RuntimeKind.TEST_APP, event.getRuntimeKind());
    assertEquals(ExposureStrategy.TEMP_PORT, event.getExposureStrategy());
    assertEquals(RuntimeStatus.RUNNING, event.getStatus());
  }

  @Test
  void shouldInferStableDomainWhenDockerTestAppDoesNotExposeHostPorts() {
    RecordingDevRuntimeEventPublisher publisher = new RecordingDevRuntimeEventPublisher();
    DockerRuntimeEventBridge bridge = new DockerRuntimeEventBridge(publisher);

    Map<String, Object> attributes = new LinkedHashMap<String, Object>();
    attributes.put(DockerRuntimeMapper.TEST_APP_LABEL, "CRM");
    attributes.put(DockerRuntimeMapper.APP_NAME_LABEL, "crm");
    attributes.put(DockerRuntimeMapper.PROFILE_LABEL, "alice");

    bridge.onEvent(
        ContainerEventType.CONTAINER_STATUS_CHANGE,
        new SimpleEventObject("cid-2", ContainerStatus.RUNNING, attributes)
    );

    DevRuntimeEvent event = publisher.events().get(0);
    assertEquals(RuntimeKind.TEST_APP, event.getRuntimeKind());
    assertEquals(ExposureStrategy.STABLE_DOMAIN, event.getExposureStrategy());
  }

  @Test
  void shouldKeepExposureStrategyNullForNonTestAppDockerEvents() {
    RecordingDevRuntimeEventPublisher publisher = new RecordingDevRuntimeEventPublisher();
    DockerRuntimeEventBridge bridge = new DockerRuntimeEventBridge(publisher);

    Map<String, Object> attributes = new LinkedHashMap<String, Object>();
    attributes.put(DockerRuntimeMapper.DEV_HELPER_LABEL, "helper");
    attributes.put(DockerRuntimeMapper.APP_NAME_LABEL, "crm");

    bridge.onEvent(
        ContainerEventType.CONTAINER_STATUS_CHANGE,
        new SimpleEventObject("cid-3", ContainerStatus.RUNNING, attributes)
    );

    DevRuntimeEvent event = publisher.events().get(0);
    assertEquals(RuntimeKind.DEV_HELPER, event.getRuntimeKind());
    assertNull(event.getExposureStrategy());
  }
}
