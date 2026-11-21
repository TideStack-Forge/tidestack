package com.ouroboros.statistics.reporting.runtime.chart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ChartOptionSupport {
  private ChartOptionSupport() {
  }

  static Number number(Object value) {
    if (value instanceof Number number) {
      return number;
    }
    if (value == null) {
      return Integer.valueOf(0);
    }
    try {
      return Double.valueOf(value.toString());
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("chart metric value is not numeric: " + value, error);
    }
  }

  static Number add(Number left, Object right) {
    Number rightNumber = number(right);
    if (left instanceof Double || left instanceof Float || rightNumber instanceof Double || rightNumber instanceof Float) {
      return Double.valueOf(left.doubleValue() + rightNumber.doubleValue());
    }
    return Long.valueOf(left.longValue() + rightNumber.longValue());
  }

  static List<Map<String, Object>> limitedRows(ChartParameters parameters, ChartProjection projection) {
    int limit = parameters.integer("limit", Integer.MAX_VALUE);
    List<Map<String, Object>> rows = projection.rows();
    return new ArrayList<Map<String, Object>>(rows.subList(0, Math.min(rows.size(), limit)));
  }

  static Map<String, Object> title(ChartParameters parameters) {
    String titleText = parameters.string("titleText", null);
    if (titleText == null) {
      return null;
    }
    Map<String, Object> title = new LinkedHashMap<String, Object>();
    title.put("text", titleText);
    title.put("left", "left");
    String subtitleText = parameters.string("subtitleText", null);
    if (subtitleText != null) {
      title.put("subtext", subtitleText);
    }
    return title;
  }

  static Map<String, Object> legend(ChartParameters parameters) {
    Map<String, Object> legend = new LinkedHashMap<String, Object>();
    legend.put("show", Boolean.valueOf(parameters.bool("showLegend", true)));
    legend.put("left", "center");
    legend.put("top", 0);
    return legend;
  }

  static void addTitle(Map<String, Object> option, ChartParameters parameters) {
    Map<String, Object> title = title(parameters);
    if (title != null) {
      option.put("title", title);
    }
  }
}
