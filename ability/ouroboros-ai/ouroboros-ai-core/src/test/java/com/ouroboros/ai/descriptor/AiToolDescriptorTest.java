package com.ouroboros.ai.descriptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiToolDescriptorTest {

  @Test
  void toolDescriptor_supportsBindingReference() {
    var descriptor = AiToolDescriptor.builder()
        .toolName("query-order")
        .bindingRef("logicflow:query-order")
        .timeoutMillis(3000)
        .build();

    assertEquals("logicflow:query-order", descriptor.getBindingRef());
    assertEquals(Integer.valueOf(3000), descriptor.getTimeoutMillis());
  }
}
