package com.ouroboros.configuration.model.workbench;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.model.ConfigurationValueModel;
import com.ouroboros.uischema.schema.SchemaNode;

public class ConfigurationWorkbenchItemPanel implements Serializable {

  private static final long serialVersionUID = 1L;

  private String key;

  private String label;

  private String groupPath;

  private Object value;

  private String revision;

  private SchemaNode editorSchema;

  private ConfigurationMetadata metadata;

  private ConfigurationValueModel configValue;

  private List<ConfigurationWorkbenchBreadcrumb> breadcrumbs = new ArrayList<>();

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

  public String getGroupPath() {
    return groupPath;
  }

  public void setGroupPath(String groupPath) {
    this.groupPath = groupPath;
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

  public List<ConfigurationWorkbenchBreadcrumb> getBreadcrumbs() {
    return breadcrumbs;
  }

  public void setBreadcrumbs(List<ConfigurationWorkbenchBreadcrumb> breadcrumbs) {
    this.breadcrumbs = breadcrumbs;
  }
}
