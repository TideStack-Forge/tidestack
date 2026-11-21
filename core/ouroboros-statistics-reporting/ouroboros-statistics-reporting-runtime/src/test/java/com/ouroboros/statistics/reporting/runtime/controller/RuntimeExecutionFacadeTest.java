package com.ouroboros.statistics.reporting.runtime.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.chart.CartesianChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.service.ChartCompiler;
import com.ouroboros.statistics.reporting.runtime.service.ChartExecutionService;
import com.ouroboros.statistics.reporting.runtime.chart.ChartOptionBuilderRegistry;
import com.ouroboros.statistics.reporting.runtime.chart.ChartParameterParser;
import com.ouroboros.statistics.reporting.runtime.chart.DataChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.chart.PieChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.chart.SankeyChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.chart.ScatterChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.center.DefaultChartDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionService;
import com.ouroboros.statistics.reporting.runtime.projector.ChartProjector;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.provider.ChartDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.statistics.reporting.validation.ChartDefinitionValidator;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeExecutionFacadeTest {

  @Test
  void shouldExportChartWithOnlyExportableFields() throws Exception {
    DatasetDefinition dataset = dataset();
    ChartDefinition chart = chart();
    DatasetExecutionService executionService = request -> datasetResult(dataset.getSchema(), rows());

    ChartExecutionController controller = new ChartExecutionController(
        datasetCenter(dataset),
        chartCenter(chart),
        executionService,
        chartExecutionService(dataset, chart, executionService),
        new ChartDefinitionValidator(),
        (loadedDataset, request) -> true,
        request -> request == null ? Collections.<String, Object>emptyMap() : request.getExecutionContext()
    );

    WebApiResult<Map<String, Object>> response = controller.export(chart.getName(), new DatasetExecutionRequest());

    assertEquals(0, response.getCode());
    List<Map<String, Object>> columns = mapList(response.getData().get("columns"));
    assertEquals(1, columns.size());
    assertEquals("id", columns.get(0).get("name"));
    List<Map<String, Object>> exportedRows = mapList(response.getData().get("rows"));
    assertEquals(1, exportedRows.size());
    assertFalse(exportedRows.get(0).containsKey("secret"));

    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    var httpResponse = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
        "/api/runtime/statistics-reporting/charts/demo.sales.chart/export"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andReturn().getResponse();
    assertEquals("A-1", com.ouroboros.util.Json.mapperCopy().readTree(httpResponse.getContentAsString())
        .at("/data/rows/0/id").asText());
  }

  private static DatasetDefinition dataset() {
    DatasetDefinition dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setIsEnabled(Boolean.TRUE);
    SqlQueryDefinition query = new SqlQueryDefinition();
    query.setSql("select 1");
    dataset.setQueryDefinition(query);
    dataset.setSchema(schema());
    return dataset;
  }

  private static ChartDefinition chart() {
    ChartDefinition chart = new ChartDefinition();
    chart.setName("demo.sales.chart");
    chart.setDatasetName("demo.sales");
    chart.setIsEnabled(Boolean.TRUE);
    chart.setChartType("metric-card");
    chart.setParams(Collections.<String, Object>singletonMap("valueFields", Arrays.asList("id", "secret")));
    return chart;
  }

  private static DatasetSchemaDefinition schema() {
    DatasetSchemaDefinition schema = new DatasetSchemaDefinition();
    schema.setFields(Arrays.asList(
        field("id", true),
        field("secret", false)
    ));
    return schema;
  }

  private static DatasetFieldDefinition field(String name, boolean exportable) {
    DatasetFieldDefinition field = new DatasetFieldDefinition();
    field.setName(name);
    field.setTitle(name);
    field.setDataType("integer");
    field.setSemanticRole("metric");
    field.setExportable(Boolean.valueOf(exportable));
    return field;
  }

  private static List<Map<String, Object>> rows() {
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("id", "A-1");
    row.put("secret", "hidden");
    return Collections.singletonList(row);
  }

  private static DatasetResult datasetResult(DatasetSchemaDefinition schema, List<Map<String, Object>> rows) {
    DatasetResult result = new DatasetResult();
    result.setResolvedSchema(schema);
    result.setRows(rows);
    return result;
  }

  private static DefaultDatasetDefinitionCenter datasetCenter(DatasetDefinition dataset) {
    return new DefaultDatasetDefinitionCenter(Collections.<DatasetDefinitionProvider>singletonList(
        () -> Collections.singletonList(dataset)
    ));
  }

  private static DefaultChartDefinitionCenter chartCenter(ChartDefinition... definitions) {
    return new DefaultChartDefinitionCenter(Collections.<ChartDefinitionProvider>singletonList(
        () -> Arrays.asList(definitions)
    ));
  }

  private static ChartExecutionService chartExecutionService(DatasetDefinition dataset,
                                                               ChartDefinition chart,
                                                               DatasetExecutionService service) {
    DefaultDatasetDefinitionCenter datasetCenter = datasetCenter(dataset);
    DefaultChartDefinitionCenter chartCenter = chartCenter(chart);
    ChartCompiler compiler = new ChartCompiler(
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
    return new ChartExecutionService(datasetCenter, chartCenter, service, compiler);
  }

  private static Map<String, Object> mapValue(Object value) {
    if (!(value instanceof Map<?, ?>)) {
      throw new AssertionError("expected map but was: " + value);
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
      if (!(entry.getKey() instanceof String)) {
        throw new AssertionError("expected string map key but was: " + entry.getKey());
      }
      result.put((String) entry.getKey(), entry.getValue());
    }
    return result;
  }

  private static List<Map<String, Object>> mapList(Object value) {
    if (!(value instanceof List<?>)) {
      throw new AssertionError("expected list but was: " + value);
    }
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Object item : (List<?>) value) {
      result.add(mapValue(item));
    }
    return result;
  }
}
