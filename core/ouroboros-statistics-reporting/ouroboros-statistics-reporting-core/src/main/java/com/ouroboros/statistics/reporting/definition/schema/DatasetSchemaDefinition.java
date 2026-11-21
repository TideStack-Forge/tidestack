package com.ouroboros.statistics.reporting.definition.schema;

import java.util.ArrayList;
import java.util.List;

public class DatasetSchemaDefinition {
  private List<DatasetFieldDefinition> fields = new ArrayList<>();

  public List<DatasetFieldDefinition> getFields() {
    return fields;
  }

  public void setFields(List<DatasetFieldDefinition> fields) {
    this.fields = fields;
  }
}
