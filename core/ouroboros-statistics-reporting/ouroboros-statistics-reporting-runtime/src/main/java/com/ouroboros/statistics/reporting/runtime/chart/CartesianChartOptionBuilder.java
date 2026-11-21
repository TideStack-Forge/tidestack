package com.ouroboros.statistics.reporting.runtime.chart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CartesianChartOptionBuilder implements ChartOptionBuilder {
  private static final Set<String> CHART_TYPES = Set.of("line", "bar", "area", "combo");

  @Override
  public Set<String> chartTypes() {
    return CHART_TYPES;
  }

  @Override
  public Map<String, Object> build(ChartParameters parameters, ChartProjection projection) {
    String xField = parameters.field("xField");
    List<SeriesDefinition> definitions = seriesDefinitions(parameters, projection);
    List<Object> categories = categories(xField, projection.rows());
    Map<String, Map<Object, Number>> values = aggregate(parameters, xField, definitions, projection.rows());
    sortAndLimit(categories, parameters, definitions, values);

    List<Map<String, Object>> series = new ArrayList<Map<String, Object>>();
    for (SeriesDefinition definition : definitions) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("name", definition.name());
      item.put("type", "area".equals(definition.type()) ? "line" : definition.type());
      item.put("data", categories.stream()
          .map(category -> values.get(definition.key()).getOrDefault(category, Integer.valueOf(0)))
          .toList());
      item.put("label", Map.of("show", Boolean.valueOf(parameters.bool("showLabel", false))));
      if ("line".equals(definition.type()) || "area".equals(definition.type())) {
        item.put("smooth", Boolean.valueOf(definition.smooth()));
      }
      if ("area".equals(definition.type())) {
        item.put("areaStyle", Map.of());
      }
      if (definition.stacked()) {
        item.put("stack", "total");
      }
      if (definition.yAxisIndex() > 0) {
        item.put("yAxisIndex", Integer.valueOf(definition.yAxisIndex()));
      }
      series.add(item);
    }

    Map<String, Object> option = new LinkedHashMap<String, Object>();
    ChartOptionSupport.addTitle(option, parameters);
    option.put("tooltip", Map.of("trigger", "axis"));
    option.put("legend", ChartOptionSupport.legend(parameters));
    option.put("xAxis", Map.of(
        "type", "category",
        "data", categories,
        "name", parameters.string("xAxisName", projection.fieldTitle(xField)),
        "boundaryGap", Boolean.valueOf("bar".equals(parameters.chartType()))));
    option.put("yAxis", yAxes(parameters, definitions, projection));
    option.put("series", series);
    return option;
  }

  private List<SeriesDefinition> seriesDefinitions(ChartParameters parameters, ChartProjection projection) {
    if ("combo".equals(parameters.chartType())) {
      return parameters.series().stream()
          .map(series -> new SeriesDefinition(
              series.field(),
              series.name() == null ? projection.fieldTitle(series.field()) : series.name(),
              series.type(),
              series.yAxisIndex(),
              series.smooth(),
              series.stacked()))
          .toList();
    }

    List<SeriesDefinition> definitions = new ArrayList<SeriesDefinition>();
    for (String field : parameters.fields("yFields")) {
      definitions.add(new SeriesDefinition(
          field,
          projection.fieldTitle(field),
          parameters.chartType(),
          0,
          parameters.bool("smooth", false),
          parameters.bool("stacked", false)));
    }
    return definitions;
  }

  private List<Object> categories(String xField, List<Map<String, Object>> rows) {
    Set<Object> categories = new LinkedHashSet<Object>();
    for (Map<String, Object> row : rows) {
      categories.add(row.get(xField));
    }
    return new ArrayList<Object>(categories);
  }

  private Map<String, Map<Object, Number>> aggregate(ChartParameters parameters,
                                                      String xField,
                                                      List<SeriesDefinition> definitions,
                                                      List<Map<String, Object>> rows) {
    Map<String, Map<Object, Number>> values = new LinkedHashMap<String, Map<Object, Number>>();
    String seriesField = parameters.field("seriesField");
    if (seriesField == null) {
      for (SeriesDefinition definition : definitions) {
        values.put(definition.key(), new LinkedHashMap<Object, Number>());
      }
      for (Map<String, Object> row : rows) {
        Object category = row.get(xField);
        for (SeriesDefinition definition : definitions) {
          Map<Object, Number> seriesValues = values.get(definition.key());
          seriesValues.put(category, ChartOptionSupport.add(
              seriesValues.getOrDefault(category, Integer.valueOf(0)), row.get(definition.field())));
        }
      }
      return values;
    }

    List<SeriesDefinition> expanded = new ArrayList<SeriesDefinition>();
    Set<String> seen = new LinkedHashSet<String>();
    for (Map<String, Object> row : rows) {
      String group = String.valueOf(row.get(seriesField));
      for (SeriesDefinition definition : definitions) {
        String name = definitions.size() > 1
            ? group + " / " + definition.name()
            : group;
        String key = group + "\u0000" + definition.field();
        if (seen.add(key)) {
          expanded.add(new SeriesDefinition(
              key, name, definition.type(), definition.yAxisIndex(), definition.smooth(), definition.stacked(),
              definition.field()));
          values.put(key, new LinkedHashMap<Object, Number>());
        }
        Object category = row.get(xField);
        Map<Object, Number> seriesValues = values.get(key);
        seriesValues.put(category, ChartOptionSupport.add(
            seriesValues.getOrDefault(category, Integer.valueOf(0)), row.get(definition.field())));
      }
    }
    definitions.clear();
    definitions.addAll(expanded);
    return values;
  }

  private void sortAndLimit(List<Object> categories,
                            ChartParameters parameters,
                            List<SeriesDefinition> definitions,
                            Map<String, Map<Object, Number>> values) {
    String xField = parameters.field("xField");
    String sortField = parameters.field("sortField");
    int direction = "desc".equals(parameters.string("sortOrder", "asc")) ? -1 : 1;
    if (sortField != null) {
      if (sortField.equals(xField)) {
        categories.sort(Comparator.comparing(String::valueOf));
      } else {
        List<SeriesDefinition> sortSeries = definitions.stream()
            .filter(series -> sortField.equals(series.field()))
            .toList();
        if (sortSeries.isEmpty()) {
          throw new IllegalArgumentException("cartesian sort field is not configured: " + sortField);
        }
        categories.sort(Comparator.comparingDouble(
            category -> sortSeries.stream()
                .mapToDouble(series -> values.get(series.key())
                    .getOrDefault(category, Integer.valueOf(0)).doubleValue())
                .sum()));
      }
      if (direction < 0) {
        java.util.Collections.reverse(categories);
      }
    }
    int limit = parameters.integer("limit", Integer.MAX_VALUE);
    if (categories.size() > limit) {
      categories.subList(limit, categories.size()).clear();
    }
  }

  private Object yAxes(ChartParameters parameters,
                       List<SeriesDefinition> definitions,
                       ChartProjection projection) {
    boolean secondaryAxis = definitions.stream().anyMatch(series -> series.yAxisIndex() > 0);
    Map<String, Object> primary = new LinkedHashMap<String, Object>();
    primary.put("type", "value");
    primary.put("name", parameters.string("yAxisName", definitions.stream()
        .filter(series -> series.yAxisIndex() == 0)
        .map(series -> projection.fieldTitle(series.field()))
        .distinct()
        .reduce((left, right) -> left + " / " + right)
        .orElse("")));
    if (!secondaryAxis) {
      return primary;
    }
    Map<String, Object> secondary = new LinkedHashMap<String, Object>();
    secondary.put("type", "value");
    secondary.put("name", definitions.stream()
        .filter(series -> series.yAxisIndex() == 1)
        .map(series -> projection.fieldTitle(series.field()))
        .distinct()
        .reduce((left, right) -> left + " / " + right)
        .orElse(""));
    return List.of(primary, secondary);
  }

  private record SeriesDefinition(String key,
                                  String name,
                                  String type,
                                  int yAxisIndex,
                                  boolean smooth,
                                  boolean stacked,
                                  String field) {
    private SeriesDefinition(String field,
                             String name,
                             String type,
                             int yAxisIndex,
                             boolean smooth,
                             boolean stacked) {
      this(field, name, type, yAxisIndex, smooth, stacked, field);
    }
  }
}
