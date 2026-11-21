package com.ouroboros.configuration.model.workbench;

import java.io.Serializable;

import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.model.ConfigurationValueModel;
import com.ouroboros.uischema.schema.SchemaNode;

public class ConfigurationWorkbenchItemSummary implements Serializable {

  private static final long serialVersionUID = 1L;

  private String key;

  private String label;

  private String description;

  private String groupPath;

  private Integer order;

  private Object value;

  private String revision;

  private Boolean modifiable;

  private Boolean obsolete;

  private SchemaNode editorSchema;

  private ConfigurationMetadata metadata;

  private ConfigurationValueModel configValue;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getGroupPath() {
    return groupPath;
  }

  public void setGroupPath(String groupPath) {
    this.groupPath = groupPath;
  }

  public Integer getOrder() {
    return order;
  }

  public void setOrder(Integer order) {
    this.order = order;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public String getRevision() {
    return revision;
  }

  public void setRevision(String revision) {
    this.revision = revision;
  }

  public Boolean getModifiable() {
    return modifiable;
  }

  public void setModifiable(Boolean modifiable) {
    this.modifiable = modifiable;
  }

  public Boolean getObsolete() {
    return obsolete;
  }

  public void setObsolete(Boolean obsolete) {
    this.obsolete = obsolete;
  }

  public SchemaNode getEditorSchema() {
    return editorSchema;
  }

  public void setEditorSchema(SchemaNode editorSchema) {
    this.editorSchema = editorSchema;
  }

  public ConfigurationMetadata getMetadata() {
    return metadata;
  }

  public void setMetadata(ConfigurationMetadata metadata) {
    this.metadata = metadata;
  }

  public ConfigurationValueModel getConfigValue() {
    return configValue;
  }

  public void setConfigValue(ConfigurationValueModel configValue) {
    this.configValue = configValue;
  }
}
