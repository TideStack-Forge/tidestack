package com.ouroboros.statistics.reporting.runtime.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.annotation.Authority;
import com.ouroboros.security.authority.AuthorityCenter;

class StatisticsReportingRuntimeAuthorityResourceTest {

  @Test
  void statisticsReportingRuntimeAuthority_isLoadedFromAuthorityCenter() {
    com.ouroboros.security.authority.Authority authority = AuthorityCenter.getAuthority("statisticsReporting.runtime")
        .orElse(null);

    assertNotNull(authority);
    assertEquals("statisticsReporting.runtime", authority.getFullName());
    List<String> apis = authority.getApis();
    assertTrue(apis.isEmpty());
  }

  @Test
  void statisticsReportingRootAuthority_isRegisteredForTreeGrouping() {
    List<com.ouroboros.security.authority.Authority> authorities = new ArrayList<com.ouroboros.security.authority.Authority>(
        AuthorityCenter.getAllAuthority().values());

    assertTrue(authorities.stream().anyMatch(authority -> "statisticsReporting".equals(authority.getFullName())));
    assertTrue(authorities.stream().anyMatch(authority -> "statisticsReporting.runtime".equals(authority.getFullName())));
  }

  @Test
  void runtimeControllers_useProjectedOrFixedDatasetAuthorities() throws NoSuchMethodException {
    assertTrue(DatasetExecutionController.class.getMethod("execute", String.class,
        com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest.class)
        .getAnnotation(Authority.class) == null);
    assertTrue(ChartExecutionController.class.getMethod("execute", String.class,
        com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest.class)
        .getAnnotation(Authority.class) == null);
    assertTrue(CompositeViewExecutionController.class.getMethod("execute", String.class,
        com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest.class)
        .getAnnotation(Authority.class) == null);
  }
}
