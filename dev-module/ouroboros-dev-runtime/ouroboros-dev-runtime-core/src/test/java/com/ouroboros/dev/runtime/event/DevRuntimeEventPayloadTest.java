package com.ouroboros.dev.runtime.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;

class DevRuntimeEventPayloadTest {

  @Test
  void shouldRetainRuntimeIdentityStatusAndFailurePayload() {
    DevRuntimeEvent event = DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_FAILED)
        .runtimeId("rt-crm-alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .runtimeGeneration(13L)
        .status(RuntimeStatus.FAILED)
        .webEndpoint("https://crm-alice.example.com")
        .apiEndpoint("https://crm-alice.example.com/api")
        .debugEndpoint("tcp://10.0.0.1:32005")
        .failureCode("HELPER_HANDSHAKE_TIMEOUT")
        .failureMessage("helper ready timeout")
        .build();

    assertEquals("rt-crm-alice", event.getRuntimeId());
    assertEquals(BackendType.KUBERNETES, event.getBackendType());
    assertEquals(ExposureStrategy.STABLE_DOMAIN, event.getExposureStrategy());
    assertEquals(Long.valueOf(13L), event.getRuntimeGeneration());
    assertEquals(RuntimeStatus.FAILED, event.getStatus());
    assertEquals("https://crm-alice.example.com", event.getWebEndpoint());
    assertEquals("HELPER_HANDSHAKE_TIMEOUT", event.getFailureCode());
  }

  @Test
  void shouldKeepFromRuntimeCompatibilityWhenExposureStrategyIsUnavailable() {
    RuntimeInstance runtime = RuntimeInstance.builder()
        .runtimeId("rt-crm-alice")
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .runtimeGeneration(13L)
        .status(RuntimeStatus.RUNNING)
        .webEndpoint("https://crm-alice.example.com")
        .build();

    DevRuntimeEvent event = DevRuntimeEvent.from(DevRuntimeEventType.RUNTIME_CREATED, runtime);

    assertEquals("rt-crm-alice", event.getRuntimeId());
    assertNull(event.getExposureStrategy());
  }

  @Test
  void shouldRetainHelperLogSnapshots() {
    DevRuntimeEvent event = DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_LOG_OUTPUT)
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .logLines(Arrays.asList("assistant started", "index ready"))
        .build();

    assertEquals(Arrays.asList("assistant started", "index ready"), event.getLogLines());
  }
}
