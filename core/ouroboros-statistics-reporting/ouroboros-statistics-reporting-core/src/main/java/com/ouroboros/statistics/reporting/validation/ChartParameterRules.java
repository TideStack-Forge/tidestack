package com.ouroboros.statistics.reporting.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;

public final class ChartParameterRules {
  private static final Set<String> CATEGORY_ROLES = values("dimension", "identifier", "detail", "time");
  private static final Set<String> METRIC_ROLES = values("metric");
  private static final Set<String> SORTABLE_ROLES = values(
      "dimension", "identifier", "detail", "time", "metric");
  private static final Set<String> TIME_DATA_TYPES = values("date", "datetime", "timestamp");
  private static final Map<String, Map<String, ParameterRule>> CHART_RULES = chartRules();
  private static final Set<String> COMBO_SERIES_PARAMETERS = values(
      "type", "field", "name", "yAxisIndex", "smooth", "stacked");

  private ChartParameterRules() {
  }

  public static boolean supportsChartType(String chartType) {
    return chartType != null && CHART_RULES.containsKey(normalize(chartType));
  }

  public static Set<String> allowedParametersFor(String chartType) {
    return rulesFor(chartType).keySet();
  }

  public static Set<String> requiredParametersFor(String chartType) {
    Set<String> required = new LinkedHashSet<String>();
    rulesFor(chartType).forEach((name, rule) -> {
      if (rule.required()) {
        required.add(name);
      }
    });
    return Collections.unmodifiableSet(required);
  }

  public static ParameterType parameterTypeFor(String chartType, String parameterName) {
    ParameterRule rule = rulesFor(chartType).get(parameterName);
    return rule == null ? null : rule.type();
  }

  public static boolean accepts(String chartType,
                                String parameterName,
                                DatasetFieldDefinition field) {
    if ("combo".equals(normalize(chartType)) && "series.field".equals(parameterName)) {
      return acceptsMetric(field);
    }
    ParameterRule rule = rulesFor(chartType).get(parameterName);
    if (rule == null || field == null) {
      return false;
    }
    return switch (rule.fieldRole()) {
      case ANY -> true;
      case CATEGORY -> acceptsCategory(field);
      case METRIC -> acceptsMetric(field);
      case SORTABLE -> acceptsCategory(field) || acceptsMetric(field);
      case NONE -> false;
    };
  }

  public static Set<String> acceptedRolesFor(String chartType, String parameterName) {
    if ("combo".equals(normalize(chartType)) && "series.field".equals(parameterName)) {
      return METRIC_ROLES;
    }
    ParameterRule rule = rulesFor(chartType).get(parameterName);
    if (rule == null) {
      return Collections.emptySet();
    }
    return switch (rule.fieldRole()) {
      case CATEGORY -> CATEGORY_ROLES;
      case METRIC -> METRIC_ROLES;
      case SORTABLE -> SORTABLE_ROLES;
      default -> Collections.emptySet();
    };
  }

  /**
   * Returns the fields that have a defined sorting meaning for the chart.
   * Sorting by an arbitrary schema field would otherwise silently fall back to
   * category ordering in the runtime builders.
   */
  public static Set<String> sortFieldCandidates(ChartDefinition chart) {
    if (chart == null || chart.getParams() == null) {
      return Collections.emptySet();
    }
    Set<String> candidates = new LinkedHashSet<String>();
    Map<String, Object> params = chart.getParams();
    addString(candidates, params.get("xField"));
    String chartType = normalize(chart.getChartType());
    if (Set.of("line", "bar", "area").contains(chartType)) {
      addStrings(candidates, params.get("yFields"));
    } else if ("combo".equals(chartType)) {
      addSeriesFields(candidates, params.get("series"));
    } else if (Set.of("pie", "donut").contains(chartType)) {
      addString(candidates, params.get("categoryField"));
      addString(candidates, params.get("valueField"));
    }
    return Collections.unmodifiableSet(candidates);
  }

  public static List<String> referencedFields(ChartDefinition chart) {
    List<String> fields = new ArrayList<String>();
    if (chart == null || chart.getParams() == null) {
      return fields;
    }
    Map<String, ParameterRule> rules = rulesFor(chart.getChartType());
    rules.forEach((name, rule) -> {
      Object value = chart.getParams().get(name);
      if (rule.type() == ParameterType.FIELD && value instanceof String field) {
        addDistinct(fields, field);
      } else if (rule.type() == ParameterType.FIELD_LIST && value instanceof List<?> values) {
        values.stream().filter(String.class::isInstance).map(String.class::cast)
            .forEach(field -> addDistinct(fields, field));
      } else if (rule.type() == ParameterType.SERIES_LIST && value instanceof List<?> values) {
        for (Object item : values) {
          if (item instanceof Map<?, ?> series && series.get("field") instanceof String field) {
            addDistinct(fields, field);
          }
        }
      }
    });
    return fields;
  }

  public static Set<String> comboSeriesParameters() {
    return COMBO_SERIES_PARAMETERS;
  }

  private static Map<String, Map<String, ParameterRule>> chartRules() {
    Map<String, Map<String, ParameterRule>> rules = new LinkedHashMap<String, Map<String, ParameterRule>>();
    rules.put("line", cartesianRules(true));
    rules.put("bar", cartesianRules(false));
    rules.put("area", cartesianRules(true));
    rules.put("pie", pieRules());
    rules.put("donut", pieRules());
    rules.put("scatter", rules(
        field("xField", true, FieldRole.METRIC),
        field("yField", true, FieldRole.METRIC),
        field("colorField", false, FieldRole.CATEGORY),
        field("sizeField", false, FieldRole.METRIC),
        bool("showLegend"), bool("showLabel"),
        string("xAxisName"), string("yAxisName"), string("titleText"), string("subtitleText"),
        positiveInteger("limit")));
    rules.put("sankey", rules(
        field("sourceField", true, FieldRole.CATEGORY),
        field("targetField", true, FieldRole.CATEGORY),
        field("valueField", true, FieldRole.METRIC),
        string("titleText"), string("subtitleText"), positiveInteger("limit")));
    rules.put("metric-card", rules(
        fieldList("valueFields", true, FieldRole.METRIC),
        string("titleText"), string("subtitleText")));
    rules.put("crosstab", rules(
        field("rowField", true, FieldRole.CATEGORY),
        field("columnField", true, FieldRole.CATEGORY),
        field("valueField", true, FieldRole.METRIC),
        string("titleText"), positiveInteger("limit")));
    rules.put("table", rules(
        fieldList("columns", true, FieldRole.ANY),
        string("titleText"), positiveInteger("limit")));
    rules.put("combo", rules(
        field("xField", true, FieldRole.CATEGORY),
        new ParameterRule("series", ParameterType.SERIES_LIST, true, FieldRole.NONE),
        bool("showLegend"), bool("showLabel"),
        string("xAxisName"), string("yAxisName"), string("titleText"), string("subtitleText"),
        field("sortField", false, FieldRole.SORTABLE), sortOrder(), positiveInteger("limit")));
    return Collections.unmodifiableMap(rules);
  }

  private static Map<String, ParameterRule> cartesianRules(boolean smooth) {
    List<ParameterRule> rules = new ArrayList<ParameterRule>();
    rules.add(field("xField", true, FieldRole.CATEGORY));
    rules.add(fieldList("yFields", true, FieldRole.METRIC));
    rules.add(field("seriesField", false, FieldRole.CATEGORY));
    rules.add(bool("stacked"));
    rules.add(bool("showLegend"));
    rules.add(bool("showLabel"));
    if (smooth) {
      rules.add(bool("smooth"));
    }
    rules.add(string("xAxisName"));
    rules.add(string("yAxisName"));
    rules.add(string("titleText"));
    rules.add(string("subtitleText"));
    rules.add(field("sortField", false, FieldRole.SORTABLE));
    rules.add(sortOrder());
    rules.add(positiveInteger("limit"));
    return rules(rules.toArray(ParameterRule[]::new));
  }

  private static Map<String, ParameterRule> pieRules() {
    return rules(
        field("categoryField", true, FieldRole.CATEGORY),
        field("valueField", true, FieldRole.METRIC),
        bool("showLegend"), bool("showLabel"),
        string("radius"), string("titleText"), string("subtitleText"),
        field("sortField", false, FieldRole.SORTABLE), sortOrder(), positiveInteger("limit"));
  }

  private static Map<String, ParameterRule> rules(ParameterRule... values) {
    Map<String, ParameterRule> rules = new LinkedHashMap<String, ParameterRule>();
    for (ParameterRule value : values) {
      rules.put(value.name(), value);
    }
    return Collections.unmodifiableMap(rules);
  }

  private static ParameterRule field(String name, boolean required, FieldRole role) {
    return new ParameterRule(name, ParameterType.FIELD, required, role);
  }

  private static ParameterRule fieldList(String name, boolean required, FieldRole role) {
    return new ParameterRule(name, ParameterType.FIELD_LIST, required, role);
  }

  private static ParameterRule bool(String name) {
    return new ParameterRule(name, ParameterType.BOOLEAN, false, FieldRole.NONE);
  }

  private static ParameterRule string(String name) {
    return new ParameterRule(name, ParameterType.STRING, false, FieldRole.NONE);
  }

  private static ParameterRule positiveInteger(String name) {
    return new ParameterRule(name, ParameterType.POSITIVE_INTEGER, false, FieldRole.NONE);
  }

  private static ParameterRule sortOrder() {
    return new ParameterRule("sortOrder", ParameterType.SORT_ORDER, false, FieldRole.NONE);
  }

  private static boolean acceptsCategory(DatasetFieldDefinition field) {
    return hasValue(field.getSemanticRole(), CATEGORY_ROLES) || hasValue(field.getDataType(), TIME_DATA_TYPES);
  }

  private static boolean acceptsMetric(DatasetFieldDefinition field) {
    return hasValue(field.getSemanticRole(), METRIC_ROLES);
  }

  private static void addStrings(Set<String> target, Object value) {
    if (value instanceof List<?> values) {
      values.forEach(field -> addString(target, field));
    }
  }

  private static void addSeriesFields(Set<String> target, Object value) {
    if (!(value instanceof List<?> series)) {
      return;
    }
    for (Object item : series) {
      if (item instanceof Map<?, ?> entry) {
        addString(target, entry.get("field"));
      }
    }
  }

  private static void addString(Set<String> target, Object value) {
    if (value instanceof String field && !field.isBlank()) {
      target.add(field);
    }
  }

  private static boolean hasValue(String value, Set<String> accepted) {
    return value != null && accepted.contains(normalize(value));
  }

  private static Set<String> values(String... values) {
    Set<String> set = new LinkedHashSet<String>();
    Collections.addAll(set, values);
    return Collections.unmodifiableSet(set);
  }

  private static Map<String, ParameterRule> rulesFor(String chartType) {
    return chartType == null
        ? Collections.emptyMap()
        : CHART_RULES.getOrDefault(normalize(chartType), Collections.emptyMap());
  }

  private static void addDistinct(List<String> values, String value) {
    if (value != null && !value.isBlank() && !values.contains(value)) {
      values.add(value);
    }
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase();
  }

  public enum ParameterType {
    FIELD,
    FIELD_LIST,
    BOOLEAN,
    STRING,
    POSITIVE_INTEGER,
    SORT_ORDER,
    SERIES_LIST
  }

  private enum FieldRole {
    NONE,
    ANY,
    CATEGORY,
    METRIC,
    SORTABLE
  }

  private record ParameterRule(String name, ParameterType type, boolean required, FieldRole fieldRole) {
  }
}
