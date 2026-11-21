package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;
import static org.junit.jupiter.api.Assertions.*;

class DatasetExecutionPolicyTest {
  @Test
  void inheritsTightensAndClampsAfterPlatformChanges() {
    var platform = DatasetExecutionPolicy.parse(Map.of("queryTimeoutSeconds", 30, "maxPageSize", 1000, "maxExportRows", 100000), "v1");
    assertSame(platform, platform.tighten(Map.of(), true));
    var tightened = platform.tighten(Map.of("maxPageSize", 100, "maxExportRows", 5000), true);
    assertEquals(100, tightened.maxPageSize());
    assertEquals(30, tightened.queryTimeoutSeconds());
    assertEquals("v1", tightened.revision());
    assertThrows(BusinessException.class, () -> platform.tighten(Map.of("maxPageSize", 1001), true));
    var lowered = new DatasetExecutionPolicy(10, 50, 100, "v2").tighten(Map.of("maxPageSize", 100), false);
    assertEquals(50, lowered.maxPageSize());
  }

  @Test
  void rejectsMalformedUnknownAndUnboundedValues() {
    var policy = DatasetExecutionPolicy.defaults();
    for (Object invalid : java.util.List.of(0, -1, 2.5, "30", Double.POSITIVE_INFINITY, Long.MAX_VALUE)) {
      assertThrows(BusinessException.class, () -> policy.tighten(Map.of("queryTimeoutSeconds", invalid), false));
    }
    assertThrows(BusinessException.class, () -> policy.tighten(Map.of("unknown", 1), true));
    assertThrows(BusinessException.class, () -> DatasetExecutionPolicy.parse(Map.of("queryTimeoutSeconds", 30), "v"));
    assertThrows(BusinessException.class, () -> DatasetExecutionPolicy.parse("invalid", "v"));
  }

  @Test
  void ignoresClientExportAndBudgetControlsDuringJsonBinding() {
    var request = Json.tryToBean(DatasetExecutionRequest.class,
        "{\"datasetName\":\"demo.sales\",\"export\":true,\"executionBudget\":{\"maxExportRows\":999999}} ").get();
    assertFalse(request.isExport());
    assertNull(request.getExecutionBudget());
    request.setExport(true);
    request.setExecutionBudget(new DatasetExecutionBudget(DatasetExecutionPolicy.defaults(), true));
    assertTrue(request.copy().isExport());
    assertSame(request.getExecutionBudget(), request.copy().getExecutionBudget());
    assertFalse(Json.toJsonString(request).contains("executionBudget"));
  }
}
