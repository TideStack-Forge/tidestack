package com.ouroboros.resource.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResourceLayerTest {

  @Test
  void parsesAppDefaultAndRuntimeOverride() {
    assertEquals(ResourceLayer.APP_DEFAULT, ResourceLayer.fromValue("app_default"));
    assertEquals(ResourceLayer.RUNTIME_OVERRIDE, ResourceLayer.fromValue("runtime_override"));
  }

  @Test
  void rejectsUnknownLayerValues() {
    assertThrows(IllegalArgumentException.class, () -> ResourceLayer.fromValue("rt"));
  }
}
