package com.xinpai.spreadsheet.db.entity;

import java.time.LocalDateTime;

public class SpreadsheetModel {
  String id;
  String name;
  String description;
  Integer is_deleted = 0;
  String app_id;
  String updated_by;
  String created_by;
  LocalDateTime created_at;
  LocalDateTime updated_at;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getIs_deleted() {
    return is_deleted;
  }

  public void setIs_deleted(Integer is_deleted) {
    this.is_deleted = is_deleted;
  }

  public String getApp_id() {
    return app_id;
  }

  public void setApp_id(String app_id) {
    this.app_id = app_id;
  }

  public LocalDateTime getCreated_at() {
    return created_at;
  }

  public void setCreated_at(LocalDateTime created_at) {
    this.created_at = created_at;
  }

  public LocalDateTime getUpdated_at() {
    return updated_at;
  }

  public void setUpdated_at(LocalDateTime updated_at) {
    this.updated_at = updated_at;
  }


  public String getUpdated_by() {
    return this.updated_by;
  }

  public void setUpdated_by(String updated_by) {
    this.updated_by = updated_by;
  }

  public String getCreated_by() {
    return this.created_by;
  }

  public void setCreated_by(String created_by) {
    this.created_by = created_by;
  }


}