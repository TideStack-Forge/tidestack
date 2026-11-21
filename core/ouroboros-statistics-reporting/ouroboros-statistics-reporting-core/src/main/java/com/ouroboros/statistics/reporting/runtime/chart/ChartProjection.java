package com.ouroboros.statistics.reporting.runtime.chart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;

public final class ChartProjection {
  private final List<Map<String, Object>> rows;
  private final DatasetSchemaDefinition schema;

  public ChartProjection(List<Map<String, Object>> rows, DatasetSchemaDefinition schema) {
    List<Map<String, Object>> copiedRows = new ArrayList<Map<String, Object>>();
    if (rows != null) {
      for (Map<String, Object> row : rows) {
        if (row != null) {
          copiedRows.add(Collections.unmodifiableMap(new LinkedHashMap<String, Object>(row)));
        }
      }
    }
    this.rows = Collections.unmodifiableList(copiedRows);
    this.schema = schema;
  }

  public List<Map<String, Object>> rows() {
    return rows;
  }

  public DatasetSchemaDefinition schema() {
    return schema;
  }

  public String fieldTitle(String fieldName) {
    if (schema != null && schema.getFields() != null) {
      for (DatasetFieldDefinition field : schema.getFields()) {
        if (field != null && fieldName.equals(field.getName()) && field.getTitle() != null) {
          return field.getTitle();
        }
      }
    }
    return fieldName;
  }
}
