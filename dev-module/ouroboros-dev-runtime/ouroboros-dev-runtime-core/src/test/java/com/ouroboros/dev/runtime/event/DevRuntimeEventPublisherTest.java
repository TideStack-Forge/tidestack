package com.ouroboros.dev.runtime.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.service.support.RuntimeContractTestSupport.RecordingApplicationEventPublisher;

class DevRuntimeEventPublisherTest {

  @Test
  void shouldPublishLifecycleAndDriftEvents() {
    RecordingApplicationEventPublisher springPublisher = new RecordingApplicationEventPublisher();
    DevRuntimeEventPublisher publisher = new SpringDevRuntimeEventPublisher(springPublisher);
    RuntimeInstance runtime = RuntimeInstance.builder()
        .runtimeId("rt-crm-alice")
        .runtimeGeneration(13L)
        .status(RuntimeStatus.RUNNING)
        .webEndpoint("https://crm-alice.example.com")
        .build();

    publisher.publish(DevRuntimeEventType.RUNTIME_STATUS_CHANGED, runtime);
    publisher.publish(DevRuntimeEventType.RUNTIME_ENDPOINT_CHANGED, runtime);
    publisher.publish(DevRuntimeEventType.RUNTIME_DELETED, runtime);
    publisher.publish(DevRuntimeEventType.RUNTIME_FAILED, runtime);

    assertEquals(
        Arrays.asList(
            DevRuntimeEventType.RUNTIME_STATUS_CHANGED,
            DevRuntimeEventType.RUNTIME_ENDPOINT_CHANGED,
            DevRuntimeEventType.RUNTIME_DELETED,
            DevRuntimeEventType.RUNTIME_FAILED
        ),
        springPublisher.eventTypes()
    );
  }
}
