package com.ouroboros.statistics.reporting.runtime.chart;

import com.ouroboros.statistics.reporting.runtime.service.ChartCompiler;

import com.ouroboros.statistics.reporting.runtime.service.ChartExecutionService;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefaultChartDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.projector.ChartProjector;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.validation.ChartDefinitionValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChartExecutionServiceTest {

  @Test
  void shouldValidateChartBeforeExecutingDataset() {
    DatasetDefinition dataset = dataset();
    ChartDefinition chart = new ChartDefinition();
    chart.setName("demo.invalid");
    chart.setDatasetName(dataset.getName());
    chart.setChartType("line");
    chart.setParams(Collections.<String, Object>singletonMap("xField", "category"));
    AtomicBoolean executed = new AtomicBoolean();
    ChartExecutionService service = new ChartExecutionService(
        new DefaultDatasetDefinitionCenter(Collections.<DatasetDefinitionProvider>singletonList(
            () -> Collections.singletonList(dataset))),
        new DefaultChartDefinitionCenter(Collections.emptyList()),
        request -> {
          executed.set(true);
          throw new AssertionError("invalid chart must not execute its dataset");
        },
        compiler()
    );

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> service.execute(chart, null));

    assertEquals("chart parameter is required for line: yFields", error.getMessage());
    assertFalse(executed.get());
  }

  private static ChartCompiler compiler() {
    return new ChartCompiler(
        new ChartDefinitionValidator(),
        new ChartParameterParser(),
        new ChartProjector(),
        new ChartOptionBuilderRegistry(Arrays.asList(
            new CartesianChartOptionBuilder(),
            new PieChartOptionBuilder(),
            new ScatterChartOptionBuilder(),
            new SankeyChartOptionBuilder(),
            new DataChartOptionBuilder()
        ))
    );
  }

  private static DatasetDefinition dataset() {
    DatasetDefinition dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    SqlQueryDefinition query = new SqlQueryDefinition();
    query.setSql("select 1");
    dataset.setQueryDefinition(query);
    DatasetSchemaDefinition schema = new DatasetSchemaDefinition();
    schema.setFields(Arrays.asList(
        field("category", "dimension"),
        field("amount", "metric")
    ));
    dataset.setSchema(schema);
    return dataset;
  }

  private static DatasetFieldDefinition field(String name, String semanticRole) {
    DatasetFieldDefinition field = new DatasetFieldDefinition();
    field.setName(name);
    field.setDataType("string");
    field.setSemanticRole(semanticRole);
    return field;
  }
}
