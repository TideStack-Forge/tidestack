package com.ouroboros.resource.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResourceScopeTest {

  @Test
  void parsesFrontendAndBackendScopeValues() {
    assertEquals(ResourceScope.FRONTEND, ResourceScope.fromValue("frontend"));
    assertEquals(ResourceScope.BACKEND, ResourceScope.fromValue("backend"));
  }

  @Test
  void rejectsUnknownScopeValues() {
    assertThrows(IllegalArgumentException.class, () -> ResourceScope.fromValue("public"));
  }
}
