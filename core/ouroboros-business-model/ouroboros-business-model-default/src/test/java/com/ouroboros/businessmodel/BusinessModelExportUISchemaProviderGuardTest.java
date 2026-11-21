package com.ouroboros.businessmodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessModelExportUISchemaProviderGuardTest {

  @Test
  void singletonHolderShouldReturnEmptyUiSchemaWhenExportIsNotInitialized() {
    BusinessModelExport.SingletonHolder holder = new BusinessModelExport.SingletonHolder();

    assertFalse(holder.getUISchema("/dev/{appName}/business-model/editor").isPresent());
    assertTrue(holder.getUISchemaMap().isEmpty());
    assertFalse(holder.getUIModel("/dev/{appName}/business-model/editor").isPresent());
    assertTrue(holder.getUIModelMap().isEmpty());
  }

  @Test
  void singletonHolderShouldReturnEmptyAuthoritiesWhenExportIsNotInitialized() {
    BusinessModelExport.SingletonHolder holder = new BusinessModelExport.SingletonHolder();

    assertTrue(holder.getAuthority("statisticsReporting.runtime").isEmpty());
    assertTrue(holder.getAllAuthorities().isEmpty());
  }
}
