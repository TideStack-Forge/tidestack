package com.ouroboros.statistics.reporting.runtime.chart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScatterChartOptionBuilder implements ChartOptionBuilder {
  @Override
  public Set<String> chartTypes() {
    return Set.of("scatter");
  }

  @Override
  public Map<String, Object> build(ChartParameters parameters, ChartProjection projection) {
    String xField = parameters.field("xField");
    String yField = parameters.field("yField");
    String colorField = parameters.field("colorField");
    String sizeField = parameters.field("sizeField");
    Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<String, List<Map<String, Object>>>();
    for (Map<String, Object> row : ChartOptionSupport.limitedRows(parameters, projection)) {
      String group = colorField == null ? "Data" : String.valueOf(row.get(colorField));
      grouped.computeIfAbsent(group, ignored -> new ArrayList<Map<String, Object>>()).add(row);
    }

    List<Map<String, Object>> series = new ArrayList<Map<String, Object>>();
    grouped.forEach((name, rows) -> {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("name", name);
      item.put("type", "scatter");
      item.put("label", Map.of("show", Boolean.valueOf(parameters.bool("showLabel", false))));
      item.put("data", rows.stream().map(row -> {
        Map<String, Object> point = new LinkedHashMap<String, Object>();
        point.put("value", List.of(ChartOptionSupport.number(row.get(xField)), ChartOptionSupport.number(row.get(yField))));
        if (sizeField != null) {
          point.put("symbolSize", Double.valueOf(Math.max(6, ChartOptionSupport.number(row.get(sizeField)).doubleValue())));
        }
        return point;
      }).toList());
      series.add(item);
    });

    Map<String, Object> option = new LinkedHashMap<String, Object>();
    ChartOptionSupport.addTitle(option, parameters);
    option.put("tooltip", Map.of("trigger", "item"));
    option.put("legend", ChartOptionSupport.legend(parameters));
    option.put("xAxis", Map.of("type", "value", "name",
        parameters.string("xAxisName", projection.fieldTitle(xField))));
    option.put("yAxis", Map.of("type", "value", "name",
        parameters.string("yAxisName", projection.fieldTitle(yField))));
    option.put("series", series);
    return option;
  }
}
