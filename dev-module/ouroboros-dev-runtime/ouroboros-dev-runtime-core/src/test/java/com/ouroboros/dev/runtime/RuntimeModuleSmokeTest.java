package com.ouroboros.dev.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.model.BackendType;

class RuntimeModuleSmokeTest {
  @Test
  void shouldExposeCoreRuntimeTypes() {
    assertEquals("DOCKER", BackendType.DOCKER.name());
  }
}
