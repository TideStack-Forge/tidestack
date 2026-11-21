package com.ouroboros.statistics.reporting.runtime.projector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.runtime.chart.ChartProjection;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartProjectorTest {

  @Test
  void shouldExposeDatasetRowsAndSchemaAsInternalProjection() {
    DatasetSchemaDefinition schema = new DatasetSchemaDefinition();
    Map<String, Object> sourceRow = new LinkedHashMap<String, Object>();
    sourceRow.put("amount", Integer.valueOf(12));
    DatasetResult result = new DatasetResult();
    result.setResolvedSchema(schema);
    result.setRows(Collections.singletonList(sourceRow));

    ChartProjection projection = new ChartProjector().project(result);

    assertSame(schema, projection.schema());
    assertEquals(Integer.valueOf(12), projection.rows().get(0).get("amount"));
    assertNotSame(sourceRow, projection.rows().get(0));
  }

  @Test
  void shouldCreateEmptyProjectionForNullDatasetResult() {
    ChartProjection projection = new ChartProjector().project(null);

    assertTrue(projection.rows().isEmpty());
    assertNull(projection.schema());
  }
}
