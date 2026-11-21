package com.ouroboros.statistics.reporting.validation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.validation.ChartParameterRules.ParameterType;

public class ChartDefinitionValidator {
  private final DatasetDefinitionValidator datasetDefinitionValidator = new DatasetDefinitionValidator();

  public void validate(DatasetDefinition dataset, ChartDefinition chart) {
    datasetDefinitionValidator.validate(dataset);
    if (chart == null) {
      throw new IllegalArgumentException("chart definition is required");
    }
    validateName(chart.getName());
    if (isBlank(chart.getDatasetName())) {
      throw new IllegalArgumentException("datasetName is required");
    }
    if (!chart.getDatasetName().equalsIgnoreCase(dataset.getName())) {
      throw new IllegalArgumentException("chart datasetName does not match dataset: " + chart.getDatasetName());
    }
    if (!"chart".equals(chart.getType())) {
      throw new IllegalArgumentException("chart type must be chart");
    }
    if (!ChartParameterRules.supportsChartType(chart.getChartType())) {
      throw new IllegalArgumentException("unsupported chartType: " + chart.getChartType());
    }
    if (!chart.getChartType().equals(normalize(chart.getChartType()))) {
      throw new IllegalArgumentException("chartType must use the canonical lowercase name: " + chart.getChartType());
    }

    validateParameters(chart, fieldMap(dataset));
  }

  public static void validateName(String name) {
    StatisticsDefinitionName.validate(name, "chart");
  }

  private void validateParameters(ChartDefinition chart, Map<String, DatasetFieldDefinition> fieldMap) {
    Map<String, Object> params = chart.getParams();
    if (params == null) {
      throw new IllegalArgumentException("chart params are required");
    }

    for (String parameterName : params.keySet()) {
      if (!ChartParameterRules.allowedParametersFor(chart.getChartType()).contains(parameterName)) {
        throw new IllegalArgumentException("unsupported chart parameter for " + chart.getChartType() + ": "
            + parameterName);
      }
      validateParameter(chart, parameterName, params.get(parameterName), fieldMap);
    }
    for (String required : ChartParameterRules.requiredParametersFor(chart.getChartType())) {
      if (!params.containsKey(required) || params.get(required) == null) {
        throw new IllegalArgumentException("chart parameter is required for " + chart.getChartType() + ": " + required);
      }
    }
  }

  private void validateParameter(ChartDefinition chart,
                                 String name,
                                 Object value,
                                 Map<String, DatasetFieldDefinition> fieldMap) {
    ParameterType type = ChartParameterRules.parameterTypeFor(chart.getChartType(), name);
    switch (type) {
      case FIELD -> validateFieldParameter(chart, name, requireString(name, value), fieldMap);
      case FIELD_LIST -> validateFieldList(chart, name, value, fieldMap);
      case BOOLEAN -> requireType(name, value, Boolean.class);
      case STRING -> requireString(name, value);
      case POSITIVE_INTEGER -> validatePositiveInteger(name, value);
      case SORT_ORDER -> validateSortOrder(name, value);
      case SERIES_LIST -> validateComboSeries(chart, value, fieldMap);
    }
  }

  private void validateFieldParameter(ChartDefinition chart,
                                      String parameterName,
                                      String fieldName,
                                      Map<String, DatasetFieldDefinition> fieldMap) {
    if ("sortField".equals(parameterName)) {
      validateSortField(chart, fieldName, fieldMap);
      return;
    }
    validateField(chart, parameterName, fieldName, fieldMap);
  }

  private void validateSortField(ChartDefinition chart,
                                 String fieldName,
                                 Map<String, DatasetFieldDefinition> fieldMap) {
    validateField(chart, "sortField", fieldName, fieldMap);
    if (!ChartParameterRules.sortFieldCandidates(chart).contains(fieldName)) {
      throw new IllegalArgumentException("chart parameter sortField must reference a configured chart field: "
          + fieldName);
    }
  }

  private void validateFieldList(ChartDefinition chart,
                                 String name,
                                 Object value,
                                 Map<String, DatasetFieldDefinition> fieldMap) {
    if (!(value instanceof List<?> values) || values.isEmpty()) {
      throw new IllegalArgumentException("chart parameter must be a non-empty string list: " + name);
    }
    for (Object field : values) {
      validateField(chart, name, requireString(name, field), fieldMap);
    }
  }

  private void validateField(ChartDefinition chart,
                             String parameterName,
                             String fieldName,
                             Map<String, DatasetFieldDefinition> fieldMap) {
    DatasetFieldDefinition field = fieldMap.get(fieldName);
    if (field == null) {
      throw new IllegalArgumentException("chart parameter " + parameterName
          + " references unknown dataset field: " + fieldName);
    }
    if (!ChartParameterRules.accepts(chart.getChartType(), parameterName, field)) {
      throw new IllegalArgumentException("chart parameter " + parameterName + " must match roles "
          + ChartParameterRules.acceptedRolesFor(chart.getChartType(), parameterName) + ": " + fieldName);
    }
  }

  private void validateComboSeries(ChartDefinition chart,
                                   Object value,
                                   Map<String, DatasetFieldDefinition> fieldMap) {
    if (!(value instanceof List<?> series) || series.isEmpty()) {
      throw new IllegalArgumentException("chart parameter must be a non-empty series list: series");
    }
    for (Object item : series) {
      if (!(item instanceof Map<?, ?> entry)) {
        throw new IllegalArgumentException("combo series item must be an object");
      }
      for (Object key : entry.keySet()) {
        if (!(key instanceof String name) || !ChartParameterRules.comboSeriesParameters().contains(name)) {
          throw new IllegalArgumentException("unsupported combo series parameter: " + key);
        }
      }
      String type = requireString("series.type", entry.get("type"));
      if (!List.of("line", "bar", "area").contains(type)) {
        throw new IllegalArgumentException("unsupported combo series type: " + type);
      }
      String fieldName = requireString("series.field", entry.get("field"));
      DatasetFieldDefinition field = fieldMap.get(fieldName);
      if (field == null) {
        throw new IllegalArgumentException("combo series references unknown dataset field: " + fieldName);
      }
      if (!"metric".equals(normalize(field.getSemanticRole()))) {
        throw new IllegalArgumentException("combo series field must be a metric: " + fieldName);
      }
      validateOptionalType(entry, "name", String.class);
      validateOptionalType(entry, "smooth", Boolean.class);
      validateOptionalType(entry, "stacked", Boolean.class);
      if (entry.containsKey("yAxisIndex")) {
        Object axis = entry.get("yAxisIndex");
        if (!(axis instanceof Number number) || number.intValue() < 0 || number.intValue() > 1
            || number.doubleValue() != number.intValue()) {
          throw new IllegalArgumentException("combo series yAxisIndex must be 0 or 1");
        }
      }
    }
  }

  private String requireString(String name, Object value) {
    if (!(value instanceof String text) || text.isBlank()) {
      throw new IllegalArgumentException("chart parameter must be a non-blank string: " + name);
    }
    return text;
  }

  private void requireType(String name, Object value, Class<?> type) {
    if (!type.isInstance(value)) {
      throw new IllegalArgumentException("chart parameter has invalid type: " + name);
    }
  }

  private void validateOptionalType(Map<?, ?> values, String name, Class<?> type) {
    if (values.containsKey(name)) {
      requireType("series." + name, values.get(name), type);
    }
  }

  private void validatePositiveInteger(String name, Object value) {
    if (!(value instanceof Number number) || number.intValue() <= 0 || number.doubleValue() != number.intValue()) {
      throw new IllegalArgumentException("chart parameter must be a positive integer: " + name);
    }
  }

  private void validateSortOrder(String name, Object value) {
    String sortOrder = requireString(name, value);
    if (!List.of("asc", "desc").contains(sortOrder)) {
      throw new IllegalArgumentException("chart parameter sortOrder must be asc or desc");
    }
  }

  private Map<String, DatasetFieldDefinition> fieldMap(DatasetDefinition dataset) {
    Map<String, DatasetFieldDefinition> fieldMap = new LinkedHashMap<String, DatasetFieldDefinition>();
    for (DatasetFieldDefinition field : dataset.getSchema().getFields()) {
      if (field != null && field.getName() != null) {
        fieldMap.put(field.getName(), field);
      }
    }
    return fieldMap;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase();
  }

}
