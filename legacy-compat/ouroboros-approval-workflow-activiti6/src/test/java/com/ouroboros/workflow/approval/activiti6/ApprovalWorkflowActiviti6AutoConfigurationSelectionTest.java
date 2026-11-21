package com.ouroboros.workflow.approval.activiti6;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.support.SpringFactoriesLoader;

class ApprovalWorkflowActiviti6AutoConfigurationSelectionTest {

  @Test
  void autoConfigurationShouldRequireExplicitActivitiEngineSelection() {
    ConditionalOnProperty annotation = ApprovalWorkflowActiviti6AutoConfiguration.class.getAnnotation(ConditionalOnProperty.class);

    assertNotNull(annotation);
    assertArrayEquals(new String[]{"ouroboros.workflow.approval.engine"}, annotation.name());
    assertEquals("activiti6", annotation.havingValue());
    assertFalse(annotation.matchIfMissing());
  }

  @Test
  void autoConfigurationShouldBeDiscoverableThroughSpringFactories() {
    List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(
        EnableAutoConfiguration.class,
        Thread.currentThread().getContextClassLoader());

    assertFalse(factoryNames.isEmpty());
    assertTrue(factoryNames.contains(ApprovalWorkflowActiviti6AutoConfiguration.class.getName()));
  }

  @Test
  void autoConfigurationShouldNotRegisterVendorAutoConfigurationFilter() {
    List<String> filterNames = SpringFactoriesLoader.loadFactoryNames(
        AutoConfigurationImportFilter.class,
        Thread.currentThread().getContextClassLoader());

    assertFalse(filterNames.contains("com.ouroboros.workflow.approval.activiti6.config.ActivitiAutoConfigurationFilter"));
  }
}
