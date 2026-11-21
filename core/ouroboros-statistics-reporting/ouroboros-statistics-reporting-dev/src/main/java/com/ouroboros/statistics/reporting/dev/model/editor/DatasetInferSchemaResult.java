package com.ouroboros.statistics.reporting.dev.model.editor;

import java.util.ArrayList;
import java.util.List;

import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;

public class DatasetInferSchemaResult {
  private List<DatasetFieldDefinition> fields = new ArrayList<DatasetFieldDefinition>();
  private List<String> warnings = new ArrayList<String>();

  public List<DatasetFieldDefinition> getFields() {
    return fields;
  }

  public void setFields(List<DatasetFieldDefinition> fields) {
    this.fields = fields;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<String> warnings) {
    this.warnings = warnings;
  }
}
