package com.ouroboros.statistics.reporting.validation;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartDefinitionValidatorTest {

  @ParameterizedTest
  @ValueSource(strings = {"demo/*", "demo/{name}", "demo?name", "demo%2Fname", "demo\\name", "demo name",
      ".", "..", "demo..name", "demo.", ".demo", "demo\nname"})
  void shouldRejectChartNamesThatCannotBeLiteralPermissionPaths(String name) {
    ChartDefinition chart = chart("line", Map.of("xField", "region", "yFields", List.of("total")));
    chart.setName(name);

    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> new ChartDefinitionValidator().validate(dataset(), chart)).getMessage().contains("chart name"));
  }

  @Test
  void shouldRejectUnknownSchemaField() {
    ChartDefinition chart = chart("bar", Map.of("xField", "region", "yFields", List.of("missingMetric")));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new ChartDefinitionValidator().validate(dataset(), chart));

    assertTrue(error.getMessage().contains("missingMetric"));
  }

  @Test
  void shouldRejectMetricParameterBoundToDimension() {
    ChartDefinition chart = chart("bar", Map.of("xField", "name", "yFields", List.of("region")));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new ChartDefinitionValidator().validate(dataset(), chart));

    assertTrue(error.getMessage().contains("yFields"));
    assertTrue(error.getMessage().contains("region"));
  }

  @Test
  void shouldValidateTypeSpecificCartesianParameters() {
    ChartDefinition chart = chart("line", Map.of(
        "xField", "id",
        "yFields", List.of("total"),
        "seriesField", "name",
        "smooth", true));

    new ChartDefinitionValidator().validate(dataset(), chart);
  }

  @Test
  void shouldValidatePieAndSankeyParameters() {
    new ChartDefinitionValidator().validate(dataset(), chart("pie", Map.of(
        "categoryField", "region", "valueField", "total")));
    new ChartDefinitionValidator().validate(dataset(), chart("sankey", Map.of(
        "sourceField", "name", "targetField", "region", "valueField", "total")));
  }

  @Test
  void shouldRejectUnknownAndMissingParameters() {
    ChartDefinition unknown = chart("pie", Map.of(
        "categoryField", "region", "valueField", "total", "xField", "name"));
    ChartDefinition missing = chart("pie", Map.of("categoryField", "region"));

    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> new ChartDefinitionValidator().validate(dataset(), unknown)).getMessage().contains("xField"));
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> new ChartDefinitionValidator().validate(dataset(), missing)).getMessage().contains("valueField"));
  }

  @Test
  void shouldRejectWrongParameterType() {
    ChartDefinition chart = chart("line", Map.of("xField", "region", "yFields", "total"));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new ChartDefinitionValidator().validate(dataset(), chart));

    assertTrue(error.getMessage().contains("non-empty string list"));
  }

  @Test
  void shouldValidateComboSeriesAndRejectUnknownNestedParameter() {
    Map<String, Object> validSeries = new LinkedHashMap<String, Object>();
    validSeries.put("type", "bar");
    validSeries.put("field", "total");
    validSeries.put("yAxisIndex", 1);
    validSeries.put("stacked", true);
    validSeries.put("smooth", false);
    new ChartDefinitionValidator().validate(dataset(), chart("combo", Map.of(
        "xField", "region", "series", List.of(validSeries))));

    Map<String, Object> invalidSeries = new LinkedHashMap<String, Object>(validSeries);
    invalidSeries.put("channel", "y");
    ChartDefinition invalid = chart("combo", Map.of("xField", "region", "series", List.of(invalidSeries)));
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> new ChartDefinitionValidator().validate(dataset(), invalid)).getMessage().contains("channel"));
  }

  @Test
  void shouldValidateComboSeriesRenderingOptions() {
    Map<String, Object> series = new LinkedHashMap<String, Object>();
    series.put("type", "line");
    series.put("field", "total");
    series.put("smooth", true);
    series.put("stacked", true);

    new ChartDefinitionValidator().validate(dataset(), chart("combo", Map.of(
        "xField", "region", "series", List.of(series))));
  }

  @Test
  void shouldRejectCartesianSortFieldThatIsNotConfigured() {
    ChartDefinition chart = chart("line", Map.of(
        "xField", "region",
        "yFields", List.of("total"),
        "sortField", "name"));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new ChartDefinitionValidator().validate(dataset(), chart));

    assertTrue(error.getMessage().contains("configured chart field"));
  }

  @Test
  void shouldRejectComboAndPieSortFieldsOutsideTheirValueFields() {
    Map<String, Object> series = Map.of("type", "line", "field", "total");
    ChartDefinition combo = chart("combo", Map.of(
        "xField", "region", "series", List.of(series), "sortField", "name"));
    ChartDefinition pie = chart("pie", Map.of(
        "categoryField", "region", "valueField", "total", "sortField", "createdAt"));

    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> new ChartDefinitionValidator().validate(dataset(), combo))
        .getMessage().contains("configured chart field"));
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> new ChartDefinitionValidator().validate(dataset(), pie))
        .getMessage().contains("configured chart field"));
  }

  private static DatasetDefinition dataset() {
    DatasetDefinition dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    SqlQueryDefinition query = new SqlQueryDefinition();
    query.setSql("select ${keyword} as total");
    dataset.setQueryDefinition(query);
    ParameterDefinition keyword = new ParameterDefinition();
    keyword.setName("keyword");
    keyword.setType("string");
    dataset.setParameters(Collections.singletonList(keyword));
    dataset.setSchema(schema(
        field("id", "string", "identifier"),
        field("name", "string", "detail"),
        field("region", "string", "dimension"),
        field("createdAt", "datetime", "time"),
        field("total", "number", "metric")));
    return dataset;
  }

  private static ChartDefinition chart(String chartType, Map<String, Object> params) {
    ChartDefinition chart = new ChartDefinition();
    chart.setName("sales.summary");
    chart.setDatasetName("demo.sales");
    chart.setChartType(chartType);
    chart.setParams(params);
    return chart;
  }

  private static DatasetSchemaDefinition schema(DatasetFieldDefinition... fields) {
    DatasetSchemaDefinition schema = new DatasetSchemaDefinition();
    schema.setFields(Arrays.asList(fields));
    return schema;
  }

  private static DatasetFieldDefinition field(String name, String dataType, String semanticRole) {
    DatasetFieldDefinition field = new DatasetFieldDefinition();
    field.setName(name);
    field.setDataType(dataType);
    field.setSemanticRole(semanticRole);
    return field;
  }
}
