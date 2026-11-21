package com.ouroboros.dev.runtime.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DevRuntimeEventListenerContractTest {

  @Test
  void shouldExposeSingleOnEventCallback() throws Exception {
    assertEquals(void.class, DevRuntimeEventListener.class.getMethod("onEvent", DevRuntimeEvent.class).getReturnType());
  }
}
