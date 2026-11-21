package com.xinpai.spreadsheet.dto;

import java.time.LocalDateTime;

import com.xinpai.spreadsheet.db.entity.SpreadsheetModel;

public class SpreadsheetInfo {
  public String id;
  public String name;
  public String description;
  public LocalDateTime createdAt;
  public LocalDateTime updatedAt;
  public String createdBy;
  public String updatedBy;
  public Boolean readonly;

  public SpreadsheetInfo(SpreadsheetModel model, Boolean readonly) {
    this.id = model.getId();
    this.name = model.getName();
    this.description = model.getDescription();
    this.createdAt = model.getCreated_at();
    this.updatedAt = model.getUpdated_at();
    this.createdBy = model.getCreated_by();
    this.updatedBy = model.getUpdated_by();
    this.readonly = readonly;
  }

  public String getId() {
    return this.id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return this.description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public LocalDateTime getCreatedAt() {
    return this.createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return this.updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getCreatedBy() {
    return this.createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getUpdatedBy() {
    return this.updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public Boolean isReadonly() {
    return this.readonly;
  }

  public Boolean getReadonly() {
    return this.readonly;
  }

  public void setReadonly(Boolean readonly) {
    this.readonly = readonly;
  }

}