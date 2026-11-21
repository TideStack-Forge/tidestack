package com.ouroboros.statistics.reporting.runtime.chart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DataChartOptionBuilder implements ChartOptionBuilder {
  private static final Set<String> CHART_TYPES = Set.of("metric-card", "crosstab", "table");

  @Override
  public Set<String> chartTypes() {
    return CHART_TYPES;
  }

  @Override
  public Map<String, Object> build(ChartParameters parameters, ChartProjection projection) {
    return switch (parameters.chartType()) {
      case "metric-card" -> metricCard(parameters, projection);
      case "crosstab" -> textTable(parameters, crosstab(parameters, projection));
      case "table" -> textTable(parameters, table(parameters, projection));
      default -> throw new IllegalArgumentException("unsupported data chartType: " + parameters.chartType());
    };
  }

  private Map<String, Object> metricCard(ChartParameters parameters, ChartProjection projection) {
    Map<String, Object> firstRow = projection.rows().isEmpty()
        ? Map.of()
        : projection.rows().get(0);
    List<String> fields = parameters.fields("valueFields");
    List<Map<String, Object>> titles = new ArrayList<Map<String, Object>>();
    for (int index = 0; index < fields.size(); index++) {
      String field = fields.get(index);
      Map<String, Object> title = new LinkedHashMap<String, Object>();
      title.put("text", String.valueOf(firstRow.getOrDefault(field, "-")));
      title.put("subtext", projection.fieldTitle(field));
      title.put("left", ((index * 2 + 1) * 100 / (fields.size() * 2)) + "%");
      title.put("top", "middle");
      title.put("textAlign", "center");
      titles.add(title);
    }
    return Map.of("title", titles);
  }

  private Map<String, Object> textTable(ChartParameters parameters, List<List<Object>> cells) {
    String text = cells.stream()
        .map(row -> row.stream().map(String::valueOf).collect(Collectors.joining("    ")))
        .collect(Collectors.joining("\n"));
    Map<String, Object> style = new LinkedHashMap<String, Object>();
    style.put("text", text);
    style.put("font", "13px monospace");
    style.put("lineHeight", Integer.valueOf(24));
    style.put("fill", "#262626");
    Map<String, Object> graphic = new LinkedHashMap<String, Object>();
    graphic.put("type", "text");
    graphic.put("left", Integer.valueOf(16));
    graphic.put("top", Integer.valueOf(16));
    graphic.put("style", style);
    Map<String, Object> option = new LinkedHashMap<String, Object>();
    ChartOptionSupport.addTitle(option, parameters);
    option.put("graphic", List.of(graphic));
    return option;
  }

  private List<List<Object>> table(ChartParameters parameters, ChartProjection projection) {
    List<String> fields = parameters.fields("columns");
    List<List<Object>> table = new ArrayList<List<Object>>();
    table.add(fields.stream().map(projection::fieldTitle).map(Object.class::cast).toList());
    for (Map<String, Object> row : ChartOptionSupport.limitedRows(parameters, projection)) {
      table.add(fields.stream().map(row::get).toList());
    }
    return table;
  }

  private List<List<Object>> crosstab(ChartParameters parameters, ChartProjection projection) {
    String rowField = parameters.field("rowField");
    String columnField = parameters.field("columnField");
    String valueField = parameters.field("valueField");
    Set<Object> rowHeaders = new LinkedHashSet<Object>();
    Set<Object> columnHeaders = new LinkedHashSet<Object>();
    Map<Object, Map<Object, Number>> matrix = new LinkedHashMap<Object, Map<Object, Number>>();
    for (Map<String, Object> row : ChartOptionSupport.limitedRows(parameters, projection)) {
      Object rowHeader = row.get(rowField);
      Object columnHeader = row.get(columnField);
      rowHeaders.add(rowHeader);
      columnHeaders.add(columnHeader);
      Map<Object, Number> rowValues = matrix.computeIfAbsent(
          rowHeader, ignored -> new LinkedHashMap<Object, Number>());
      rowValues.put(columnHeader, ChartOptionSupport.add(
          rowValues.getOrDefault(columnHeader, Integer.valueOf(0)), row.get(valueField)));
    }
    List<List<Object>> table = new ArrayList<List<Object>>();
    List<Object> header = new ArrayList<Object>();
    header.add(projection.fieldTitle(rowField));
    header.addAll(columnHeaders);
    table.add(header);
    for (Object rowHeader : rowHeaders) {
      List<Object> row = new ArrayList<Object>();
      row.add(rowHeader);
      Map<Object, Number> values = matrix.get(rowHeader);
      for (Object columnHeader : columnHeaders) {
        row.add(values.get(columnHeader));
      }
      table.add(row);
    }
    return table;
  }
}
