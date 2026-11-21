package com.ouroboros.dev.app;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import com.ouroboros.data.annotation.DataModelScan;

class DevAppAutoConfigurationTest {

  @Test
  void shouldEnableDataModelScanForDevAppModels() {
    assertNotNull(DevAppAutoConfiguration.class.getAnnotation(DataModelScan.class));
  }

  @Test
  void shouldImportRuntimeLockAutoConfiguration() {
    Import importAnnotation = DevAppAutoConfiguration.class.getAnnotation(Import.class);
    assertNotNull(importAnnotation);
    assertTrue(Arrays.asList(importAnnotation.value()).contains(DevAppRuntimeLockAutoConfiguration.class));
  }
}
