package com.ouroboros.statistics.reporting.runtime.chart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChartParameters {
  private final String chartType;
  private final Map<String, Object> values;

  ChartParameters(String chartType, Map<String, Object> values) {
    this.chartType = chartType;
    this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
  }

  public String chartType() {
    return chartType;
  }

  public String field(String name) {
    return string(name, null);
  }

  public List<String> fields(String name) {
    Object value = values.get(name);
    if (!(value instanceof List<?> items)) {
      return Collections.emptyList();
    }
    List<String> fields = new ArrayList<String>();
    for (Object item : items) {
      if (item instanceof String field) {
        fields.add(field);
      }
    }
    return Collections.unmodifiableList(fields);
  }

  public String string(String name, String defaultValue) {
    Object value = values.get(name);
    return value instanceof String text ? text : defaultValue;
  }

  public boolean bool(String name, boolean defaultValue) {
    Object value = values.get(name);
    return value instanceof Boolean flag ? flag.booleanValue() : defaultValue;
  }

  public int integer(String name, int defaultValue) {
    Object value = values.get(name);
    return value instanceof Number number ? number.intValue() : defaultValue;
  }

  public List<Series> series() {
    Object value = values.get("series");
    if (!(value instanceof List<?> items)) {
      return Collections.emptyList();
    }
    List<Series> series = new ArrayList<Series>();
    for (Object item : items) {
      if (!(item instanceof Map<?, ?> entry)) {
        continue;
      }
      series.add(new Series(
          text(entry.get("type")),
          text(entry.get("field")),
          text(entry.get("name")),
          entry.get("yAxisIndex") instanceof Number axis ? axis.intValue() : 0,
          Boolean.TRUE.equals(entry.get("smooth")),
          Boolean.TRUE.equals(entry.get("stacked"))
      ));
    }
    return Collections.unmodifiableList(series);
  }

  private String text(Object value) {
    return value instanceof String text ? text : null;
  }

  public record Series(String type,
                       String field,
                       String name,
                       int yAxisIndex,
                       boolean smooth,
                       boolean stacked) {
  }
}
