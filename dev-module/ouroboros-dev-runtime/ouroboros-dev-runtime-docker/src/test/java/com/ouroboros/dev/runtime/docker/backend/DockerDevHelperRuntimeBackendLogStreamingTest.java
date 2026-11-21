package com.ouroboros.dev.runtime.docker.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.docker.support.DockerRuntimeTestSupport;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;

class DockerDevHelperRuntimeBackendLogStreamingTest {

  @Test
  void shouldPublishHelperLogSnapshotsAsRuntimeLogEvents() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService, eventPublisher);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    containerService.putLogs("ouroboros-dev-helper-crm", Arrays.asList("assistant started", "index ready"));

    backend.refreshHelperLogStreaming(RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER));

    List<DevRuntimeEvent> events = eventPublisher.events();
    assertEquals(1, events.size());
    assertEquals(DevRuntimeEventType.RUNTIME_LOG_OUTPUT, events.get(0).getType());
    assertEquals(Arrays.asList("assistant started", "index ready"), events.get(0).getLogLines());
  }

  @Test
  void shouldPublishSameSnapshotAgainAfterResettingLogStream() {
    DockerRuntimeTestSupport.FakeContainerService containerService = new DockerRuntimeTestSupport.FakeContainerService();
    RecordingRuntimeEventPublisher eventPublisher = new RecordingRuntimeEventPublisher();
    DockerDevHelperRuntimeBackend backend = new DockerDevHelperRuntimeBackend(containerService, eventPublisher);
    RuntimeIdentityKey key = RuntimeIdentityKey.of("crm", "dev-helper", RuntimeKind.DEV_HELPER, BackendType.DOCKER);

    backend.create(DockerRuntimeTestSupport.helperSpec()).get();
    backend.start(DockerRuntimeTestSupport.helperSpec()).get();
    containerService.putLogs("ouroboros-dev-helper-crm", Arrays.asList("assistant started", "index ready"));

    backend.refreshHelperLogStreaming(key);
    backend.resetLogStream(key);
    backend.refreshHelperLogStreaming(key);

    List<DevRuntimeEvent> events = eventPublisher.events();
    assertEquals(2, events.size());
    assertEquals(Arrays.asList("assistant started", "index ready"), events.get(1).getLogLines());
  }

  private static class RecordingRuntimeEventPublisher implements DevRuntimeEventPublisher {
    private final java.util.List<DevRuntimeEvent> events = new java.util.ArrayList<DevRuntimeEvent>();

    @Override
    public void publish(DevRuntimeEvent event) {
      events.add(event);
    }

    java.util.List<DevRuntimeEvent> events() {
      return events;
    }
  }
}
