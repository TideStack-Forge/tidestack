package com.ouroboros.statistics.reporting.runtime.chart;

import com.ouroboros.statistics.reporting.runtime.service.ChartCompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.projector.ChartProjector;
import com.ouroboros.statistics.reporting.runtime.result.ChartExecutionResponse;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.statistics.reporting.validation.ChartDefinitionValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartCompilerTest {

  @Test
  void shouldAggregateCartesianSeriesAndApplyTypeSpecificRendering() {
    ChartExecutionResponse line = compile("line", params(
        "xField", "category",
        "yFields", Collections.singletonList("amount"),
        "smooth", Boolean.TRUE
    ));
    Map<String, Object> lineSeries = mapList(line.getOption().get("series")).get(0);
    assertEquals("line", lineSeries.get("type"));
    assertEquals(Boolean.TRUE, lineSeries.get("smooth"));
    assertEquals(Arrays.asList(Long.valueOf(15), Long.valueOf(7)), objectList(lineSeries.get("data")));

    ChartExecutionResponse bar = compile("bar", params(
        "xField", "category",
        "yFields", Collections.singletonList("amount")
    ));
    assertEquals("bar", mapList(bar.getOption().get("series")).get(0).get("type"));
    assertEquals(Boolean.TRUE, mapValue(bar.getOption().get("xAxis")).get("boundaryGap"));

    ChartExecutionResponse area = compile("area", params(
        "xField", "category",
        "yFields", Collections.singletonList("amount")
    ));
    Map<String, Object> areaSeries = mapList(area.getOption().get("series")).get(0);
    assertEquals("line", areaSeries.get("type"));
    assertTrue(areaSeries.containsKey("areaStyle"));
  }

  @Test
  void shouldBuildComboSeriesWithIndependentTypesAndAxes() {
    ChartExecutionResponse response = compile("combo", params(
        "xField", "category",
        "series", Arrays.asList(
            params("type", "bar", "field", "amount", "name", "Sales", "smooth", Boolean.TRUE),
            params("type", "line", "field", "count", "name", "Orders", "yAxisIndex", Integer.valueOf(1),
                "smooth", Boolean.TRUE, "stacked", Boolean.TRUE)
        )
    ));

    List<Map<String, Object>> series = mapList(response.getOption().get("series"));
    assertEquals(Arrays.asList("bar", "line"), series.stream().map(item -> item.get("type")).toList());
    assertFalse(series.get(0).containsKey("smooth"));
    assertEquals(Integer.valueOf(1), series.get(1).get("yAxisIndex"));
    assertEquals(Boolean.TRUE, series.get(1).get("smooth"));
    assertEquals("total", series.get(1).get("stack"));
    assertEquals(2, objectList(response.getOption().get("yAxis")).size());
  }

  @Test
  void shouldSortCartesianByConfiguredXOrMetricAndApplyLimit() {
    ChartExecutionResponse byCategory = compile("line", params(
        "xField", "category",
        "yFields", Collections.singletonList("amount"),
        "sortField", "category",
        "sortOrder", "desc",
        "limit", Integer.valueOf(1)
    ));
    assertEquals(Collections.singletonList("B"),
        objectList(mapValue(byCategory.getOption().get("xAxis")).get("data")));

    ChartExecutionResponse byMetric = compile("line", params(
        "xField", "category",
        "yFields", Collections.singletonList("amount"),
        "sortField", "amount",
        "sortOrder", "desc",
        "limit", Integer.valueOf(1)
    ));
    assertEquals(Collections.singletonList("A"),
        objectList(mapValue(byMetric.getOption().get("xAxis")).get("data")));
  }

  @Test
  void shouldSortComboByConfiguredSeriesMetric() {
    ChartExecutionResponse response = compile("combo", params(
        "xField", "category",
        "series", Collections.singletonList(params("type", "line", "field", "count")),
        "sortField", "count",
        "sortOrder", "desc",
        "limit", Integer.valueOf(1)
    ));

    assertEquals(Collections.singletonList("A"),
        objectList(mapValue(response.getOption().get("xAxis")).get("data")));
  }

  @Test
  void shouldSortGroupedSeriesByTheirMetricTotalsBeforeApplyingLimit() {
    ChartExecutionResponse response = compile("bar", params(
        "xField", "category",
        "yFields", Collections.singletonList("amount"),
        "seriesField", "region",
        "sortField", "amount",
        "sortOrder", "desc",
        "limit", Integer.valueOf(1)
    ));

    assertEquals(Collections.singletonList("A"),
        objectList(mapValue(response.getOption().get("xAxis")).get("data")));
    List<Map<String, Object>> series = mapList(response.getOption().get("series"));
    assertEquals(List.of("North", "South"), series.stream().map(item -> item.get("name")).toList());
    assertEquals(List.of(Long.valueOf(10)), objectList(series.get(0).get("data")));
    assertEquals(List.of(Long.valueOf(5)), objectList(series.get(1).get("data")));
  }

  @Test
  void shouldBuildPieAndDonutOptionsFromAggregatedCategories() {
    ChartExecutionResponse pie = compile("pie", params(
        "categoryField", "category",
        "valueField", "amount",
        "sortField", "amount",
        "sortOrder", "desc"
    ));
    Map<String, Object> pieSeries = mapList(pie.getOption().get("series")).get(0);
    List<Map<String, Object>> pieData = mapList(pieSeries.get("data"));
    assertEquals("A", pieData.get(0).get("name"));
    assertEquals(Long.valueOf(15), pieData.get(0).get("value"));
    assertEquals("60%", pieSeries.get("radius"));

    ChartExecutionResponse donut = compile("donut", params(
        "categoryField", "category",
        "valueField", "amount"
    ));
    assertEquals(Arrays.asList("45%", "70%"),
        objectList(mapList(donut.getOption().get("series")).get(0).get("radius")));

    ChartExecutionResponse byCategory = compile("pie", params(
        "categoryField", "category",
        "valueField", "amount",
        "sortField", "category",
        "sortOrder", "asc"
    ));
    assertEquals("A", mapList(mapList(byCategory.getOption().get("series")).get(0).get("data"))
        .get(0).get("name"));
  }

  @Test
  void shouldBuildScatterAndSankeyOptions() {
    ChartExecutionResponse scatter = compile("scatter", params(
        "xField", "xMetric",
        "yField", "yMetric",
        "colorField", "region",
        "sizeField", "amount"
    ));
    List<Map<String, Object>> scatterSeries = mapList(scatter.getOption().get("series"));
    assertEquals(Arrays.asList("North", "South"),
        scatterSeries.stream().map(item -> item.get("name")).toList());
    Map<String, Object> firstPoint = mapList(scatterSeries.get(0).get("data")).get(0);
    assertEquals(Arrays.asList(Integer.valueOf(1), Integer.valueOf(2)), objectList(firstPoint.get("value")));
    assertEquals(Double.valueOf(10), firstPoint.get("symbolSize"));

    ChartExecutionResponse sankey = compile("sankey", params(
        "sourceField", "source",
        "targetField", "target",
        "valueField", "amount"
    ));
    Map<String, Object> sankeySeries = mapList(sankey.getOption().get("series")).get(0);
    assertEquals("sankey", sankeySeries.get("type"));
    assertEquals(3, mapList(sankeySeries.get("data")).size());
    assertEquals(Long.valueOf(15), mapList(sankeySeries.get("links")).get(0).get("value"));
  }

  @Test
  void shouldBuildMetricTableAndCrosstabOptions() {
    ChartExecutionResponse metric = compile("metric-card", params(
        "valueFields", Arrays.asList("amount", "count")
    ));
    List<Map<String, Object>> titles = mapList(metric.getOption().get("title"));
    assertEquals("10", titles.get(0).get("text"));
    assertEquals("Amount", titles.get(0).get("subtext"));

    ChartExecutionResponse table = compile("table", params(
        "columns", Arrays.asList("category", "amount")
    ));
    String tableText = graphicText(table);
    assertTrue(tableText.startsWith("Category    Amount"));
    assertTrue(tableText.contains("A    10"));

    ChartExecutionResponse crosstab = compile("crosstab", params(
        "rowField", "category",
        "columnField", "region",
        "valueField", "amount"
    ));
    String crosstabText = graphicText(crosstab);
    assertTrue(crosstabText.startsWith("Category    North    South"));
    assertTrue(crosstabText.contains("A    10    5"));
  }

  @Test
  void shouldRejectDuplicateAndUnsupportedOptionBuilders() {
    ChartOptionBuilder first = builder("line");
    ChartOptionBuilder duplicate = builder("line");

    IllegalArgumentException duplicateError = assertThrows(IllegalArgumentException.class,
        () -> new ChartOptionBuilderRegistry(Arrays.asList(first, duplicate)));
    assertEquals("duplicate chart option builder for chartType: line", duplicateError.getMessage());

    ChartOptionBuilderRegistry registry = new ChartOptionBuilderRegistry(Collections.singletonList(first));
    IllegalArgumentException unsupportedError = assertThrows(IllegalArgumentException.class,
        () -> registry.build(new ChartParameters("pie", Collections.emptyMap()),
            new ChartProjection(Collections.emptyList(), null)));
    assertEquals("no chart option builder registered for chartType: pie", unsupportedError.getMessage());
  }

  private static ChartExecutionResponse compile(String chartType, Map<String, Object> params) {
    DatasetDefinition dataset = dataset();
    ChartDefinition chart = new ChartDefinition();
    chart.setName("demo." + chartType);
    chart.setDatasetName(dataset.getName());
    chart.setChartType(chartType);
    chart.setParams(params);
    return compiler().compile(dataset, chart, datasetResult());
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
        field("category", "Category", "string", "dimension"),
        field("region", "Region", "string", "dimension"),
        field("source", "Source", "string", "dimension"),
        field("target", "Target", "string", "dimension"),
        field("amount", "Amount", "decimal", "metric"),
        field("count", "Count", "integer", "metric"),
        field("xMetric", "X", "decimal", "metric"),
        field("yMetric", "Y", "decimal", "metric")
    ));
    dataset.setSchema(schema);
    return dataset;
  }

  private static DatasetFieldDefinition field(String name, String title, String dataType, String semanticRole) {
    DatasetFieldDefinition field = new DatasetFieldDefinition();
    field.setName(name);
    field.setTitle(title);
    field.setDataType(dataType);
    field.setSemanticRole(semanticRole);
    return field;
  }

  private static DatasetResult datasetResult() {
    DatasetResult result = new DatasetResult();
    result.setResolvedSchema(dataset().getSchema());
    result.setRows(Arrays.asList(
        params("category", "A", "region", "North", "source", "Visit", "target", "Browse",
            "amount", Integer.valueOf(10), "count", Integer.valueOf(2),
            "xMetric", Integer.valueOf(1), "yMetric", Integer.valueOf(2)),
        params("category", "A", "region", "South", "source", "Visit", "target", "Browse",
            "amount", Integer.valueOf(5), "count", Integer.valueOf(3),
            "xMetric", Integer.valueOf(2), "yMetric", Integer.valueOf(4)),
        params("category", "B", "region", "North", "source", "Browse", "target", "Buy",
            "amount", Integer.valueOf(7), "count", Integer.valueOf(1),
            "xMetric", Integer.valueOf(3), "yMetric", Integer.valueOf(6))
    ));
    return result;
  }

  private static ChartOptionBuilder builder(String chartType) {
    return new ChartOptionBuilder() {
      @Override
      public Set<String> chartTypes() {
        return Collections.singleton(chartType);
      }

      @Override
      public Map<String, Object> build(ChartParameters parameters, ChartProjection projection) {
        return Collections.emptyMap();
      }
    };
  }

  private static Map<String, Object> params(Object... values) {
    Map<String, Object> params = new LinkedHashMap<String, Object>();
    for (int index = 0; index < values.length; index += 2) {
      params.put((String) values[index], values[index + 1]);
    }
    return params;
  }

  private static String graphicText(ChartExecutionResponse response) {
    Map<String, Object> graphic = mapList(response.getOption().get("graphic")).get(0);
    return String.valueOf(mapValue(graphic.get("style")).get("text"));
  }

  private static Map<String, Object> mapValue(Object value) {
    if (!(value instanceof Map<?, ?> source)) {
      throw new AssertionError("expected map but was: " + value);
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new AssertionError("expected string map key but was: " + entry.getKey());
      }
      result.put(key, entry.getValue());
    }
    return result;
  }

  private static List<Map<String, Object>> mapList(Object value) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Object item : objectList(value)) {
      result.add(mapValue(item));
    }
    return result;
  }

  private static List<Object> objectList(Object value) {
    if (!(value instanceof List<?> source)) {
      throw new AssertionError("expected list but was: " + value);
    }
    return new ArrayList<Object>(source);
  }
}
