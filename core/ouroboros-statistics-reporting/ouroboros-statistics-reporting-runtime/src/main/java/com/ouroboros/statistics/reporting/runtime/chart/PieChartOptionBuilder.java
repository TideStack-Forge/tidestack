package com.ouroboros.statistics.reporting.runtime.chart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PieChartOptionBuilder implements ChartOptionBuilder {
  private static final Set<String> CHART_TYPES = Set.of("pie", "donut");

  @Override
  public Set<String> chartTypes() {
    return CHART_TYPES;
  }

  @Override
  public Map<String, Object> build(ChartParameters parameters, ChartProjection projection) {
    String categoryField = parameters.field("categoryField");
    String valueField = parameters.field("valueField");
    Map<String, Number> totals = new LinkedHashMap<String, Number>();
    for (Map<String, Object> row : projection.rows()) {
      String category = String.valueOf(row.get(categoryField));
      totals.put(category, ChartOptionSupport.add(
          totals.getOrDefault(category, Integer.valueOf(0)), row.get(valueField)));
    }
    List<Map.Entry<String, Number>> entries = new ArrayList<Map.Entry<String, Number>>(totals.entrySet());
    String sortField = parameters.field("sortField");
    if (sortField != null) {
      if (categoryField.equals(sortField)) {
        entries.sort(Map.Entry.comparingByKey());
      } else if (valueField.equals(sortField)) {
        entries.sort(Comparator.comparingDouble(entry -> entry.getValue().doubleValue()));
      } else {
        throw new IllegalArgumentException("pie sort field is not configured: " + sortField);
      }
      if ("desc".equals(parameters.string("sortOrder", "desc"))) {
        java.util.Collections.reverse(entries);
      }
    }
    int limit = Math.min(entries.size(), parameters.integer("limit", Integer.MAX_VALUE));
    List<Map<String, Object>> data = entries.subList(0, limit).stream()
        .map(entry -> Map.<String, Object>of("name", entry.getKey(), "value", entry.getValue()))
        .toList();

    Map<String, Object> series = new LinkedHashMap<String, Object>();
    series.put("type", "pie");
    series.put("name", projection.fieldTitle(valueField));
    series.put("radius", "donut".equals(parameters.chartType())
        ? List.of("45%", "70%")
        : parameters.string("radius", "60%"));
    series.put("label", Map.of("show", Boolean.valueOf(parameters.bool("showLabel", true))));
    series.put("data", data);

    Map<String, Object> option = new LinkedHashMap<String, Object>();
    ChartOptionSupport.addTitle(option, parameters);
    option.put("tooltip", Map.of("trigger", "item"));
    option.put("legend", ChartOptionSupport.legend(parameters));
    option.put("series", List.of(series));
    return option;
  }
}
