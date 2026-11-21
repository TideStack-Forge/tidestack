package com.ouroboros.statistics.reporting.runtime.chart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SankeyChartOptionBuilder implements ChartOptionBuilder {
  @Override
  public Set<String> chartTypes() {
    return Set.of("sankey");
  }

  @Override
  public Map<String, Object> build(ChartParameters parameters, ChartProjection projection) {
    String sourceField = parameters.field("sourceField");
    String targetField = parameters.field("targetField");
    String valueField = parameters.field("valueField");
    Set<String> nodeNames = new LinkedHashSet<String>();
    Map<String, Number> linkValues = new LinkedHashMap<String, Number>();
    Map<String, List<String>> linkNodes = new LinkedHashMap<String, List<String>>();
    for (Map<String, Object> row : ChartOptionSupport.limitedRows(parameters, projection)) {
      String source = String.valueOf(row.get(sourceField));
      String target = String.valueOf(row.get(targetField));
      String key = source + "\u0000" + target;
      nodeNames.add(source);
      nodeNames.add(target);
      linkNodes.put(key, List.of(source, target));
      linkValues.put(key, ChartOptionSupport.add(
          linkValues.getOrDefault(key, Integer.valueOf(0)), row.get(valueField)));
    }

    List<Map<String, Object>> links = new ArrayList<Map<String, Object>>();
    linkValues.forEach((key, value) -> links.add(Map.of(
        "source", linkNodes.get(key).get(0),
        "target", linkNodes.get(key).get(1),
        "value", value)));
    Map<String, Object> series = new LinkedHashMap<String, Object>();
    series.put("type", "sankey");
    series.put("data", nodeNames.stream().map(name -> Map.of("name", name)).toList());
    series.put("links", links);
    series.put("emphasis", Map.of("focus", "adjacency"));

    Map<String, Object> option = new LinkedHashMap<String, Object>();
    ChartOptionSupport.addTitle(option, parameters);
    option.put("tooltip", Map.of("trigger", "item"));
    option.put("series", List.of(series));
    return option;
  }
}
