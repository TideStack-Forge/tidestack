package com.ouroboros.dev.datasource.dto;

public class TableInfo {
  private String name;
  private String remarks;
  private String type;

  public String getName() {
    return name;
  }

  public TableInfo setName(String name) {
    this.name = name;
    return this;
  }

  public String getRemarks() {
    return remarks;
  }

  public TableInfo setRemarks(String remarks) {
    this.remarks = remarks;
    return this;
  }

  public String getType() {
    return type;
  }

  public TableInfo setType(String type) {
    this.type = type;
    return this;
  }
}
