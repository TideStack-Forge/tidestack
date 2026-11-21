package com.ouroboros.statistics.reporting.dev.service;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel;
import com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatasetPreviewConfigurationTest {
  @Test
  void reusesSavedParametersAndConnectionForTheSameLogicalSource() {
    var service = mock(DatasetDevConfigService.class, CALLS_REAL_METHODS);
    var config = new DatasetDevConfigModel();
    config.setDatasource("default");
    config.setParameters(Map.of("keyword", "saved", "region", "east"));
    var connection = new DatasetTestConnection();
    connection.setKind("platform"); connection.setPlatformDataSourceName("test-db");
    config.setTestConnection(connection);
    when(service.findByDataset("demo", "demo.orders")).thenReturn(Optional.of(config));
    var request = new DatasetExecutionRequest(); request.setDatasetName("demo.orders");
    request.setRuntimeParameters(Map.of("keyword", "request"));
    service.applyToExecutionRequest("demo", request, "DEFAULT");
    assertEquals(Map.of("keyword", "request", "region", "east"), request.getRuntimeParameters());
    assertSame(connection, request.getExecutionContext().get("testConnection"));
    assertEquals("DEFAULT", request.getExecutionContext().get("datasource"));
    var other = new DatasetExecutionRequest(); other.setDatasetName("demo.orders");
    service.applyToExecutionRequest("demo", other, "archive");
    assertTrue(other.getRuntimeParameters().isEmpty());
    assertFalse(other.getExecutionContext().containsKey("testConnection"));
  }

  @Test
  void leavesAnUnsavedPrivateQueryWithoutDatasetConfiguration() {
    var service = mock(DatasetDevConfigService.class, CALLS_REAL_METHODS);
    var request = new DatasetExecutionRequest(); request.setDatasetName("demo.orders.private-query");
    request.setRuntimeParameters(Map.of("id", "A01"));
    service.applyToExecutionRequest("demo", request, "default");
    assertEquals(Map.of("id", "A01"), request.getRuntimeParameters());
  }
}
