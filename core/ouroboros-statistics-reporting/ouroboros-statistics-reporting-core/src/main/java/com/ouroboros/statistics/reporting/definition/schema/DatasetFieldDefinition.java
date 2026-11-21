package com.ouroboros.statistics.reporting.definition.schema;

public class DatasetFieldDefinition {
  private String name;
  private String title;
  private String dataType;
  private String semanticRole;
  private Boolean nullable = true;
  private Boolean sortable = false;
  private Boolean filterable = false;
  private Boolean exportable = true;
  private Boolean visibleByDefault = true;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDataType() {
    return dataType;
  }

  public void setDataType(String dataType) {
    this.dataType = dataType;
  }

  public String getSemanticRole() {
    return semanticRole;
  }

  public void setSemanticRole(String semanticRole) {
    this.semanticRole = semanticRole;
  }

  public Boolean getNullable() {
    return nullable;
  }

  public boolean isNullable() {
    return !Boolean.FALSE.equals(nullable);
  }

  public void setNullable(Boolean nullable) {
    this.nullable = nullable;
  }

  public Boolean getSortable() {
    return sortable;
  }

  public boolean isSortable() {
    return Boolean.TRUE.equals(sortable);
  }

  public void setSortable(Boolean sortable) {
    this.sortable = sortable;
  }

  public Boolean getFilterable() {
    return filterable;
  }

  public boolean isFilterable() {
    return Boolean.TRUE.equals(filterable);
  }

  public void setFilterable(Boolean filterable) {
    this.filterable = filterable;
  }

  public Boolean getExportable() {
    return exportable;
  }

  public boolean isExportable() {
    return !Boolean.FALSE.equals(exportable);
  }

  public void setExportable(Boolean exportable) {
    this.exportable = exportable;
  }

  public Boolean getVisibleByDefault() {
    return visibleByDefault;
  }

  public boolean isVisibleByDefault() {
    return !Boolean.FALSE.equals(visibleByDefault);
  }

  public void setVisibleByDefault(Boolean visibleByDefault) {
    this.visibleByDefault = visibleByDefault;
  }
}
