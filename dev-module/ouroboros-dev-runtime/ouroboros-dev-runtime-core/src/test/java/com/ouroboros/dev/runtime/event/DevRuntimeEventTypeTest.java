package com.ouroboros.dev.runtime.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DevRuntimeEventTypeTest {

  @Test
  void shouldExposeUnifiedRuntimeEventTypes() {
    assertEquals(DevRuntimeEventType.RUNTIME_CREATED, DevRuntimeEventType.valueOf("RUNTIME_CREATED"));
    assertEquals(DevRuntimeEventType.RUNTIME_STATUS_CHANGED, DevRuntimeEventType.valueOf("RUNTIME_STATUS_CHANGED"));
    assertEquals(DevRuntimeEventType.RUNTIME_ENDPOINT_CHANGED, DevRuntimeEventType.valueOf("RUNTIME_ENDPOINT_CHANGED"));
    assertEquals(DevRuntimeEventType.RUNTIME_DELETED, DevRuntimeEventType.valueOf("RUNTIME_DELETED"));
    assertEquals(DevRuntimeEventType.RUNTIME_FAILED, DevRuntimeEventType.valueOf("RUNTIME_FAILED"));
    assertEquals(DevRuntimeEventType.RUNTIME_LOG_OUTPUT, DevRuntimeEventType.valueOf("RUNTIME_LOG_OUTPUT"));
  }
}
