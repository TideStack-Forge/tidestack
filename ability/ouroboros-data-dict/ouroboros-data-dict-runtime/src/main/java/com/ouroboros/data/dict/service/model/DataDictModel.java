package com.ouroboros.data.dict.service.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.dict.DataDict;
import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.data.annotation.AuditFields;
import com.ouroboros.data.annotation.LogicalDelete;

/**
 * @author liansz
 */
@Model(rawName = "t_sys_dictionary", label = "数据字典")
@AuditFields
@LogicalDelete
public class DataDictModel implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 名称（主键）
   */
  @Field
  @PrimaryKey
  private String name;

  /**
   * 标题
   */
  @Field
  private String title;

  /**
   * 类型
   */
  @Field
  private String type;

  /**
   * 组
   */
  @Field(nullable = true)
  private String group;

  /**
   * 是否可编辑
   */
  @JsonProperty("isEditable")
  @Field(type = "Boolean")
  private boolean isEditable;

  /**
   * 是否树形模式
   */
  @Field(type = "Boolean")
  private boolean treeMode;

  /**
   * 创建者
   */
  @Field(nullable = true)
  private String createdBy;

  /**
   * 创建时间
   */
  @Field(nullable = true)
  private LocalDateTime createdAt;

  /**
   * 更新者
   */
  @Field(nullable = true)
  private String updatedBy;

  /**
   * 更新时间
   */
  @Field(nullable = true)
  private LocalDateTime updatedAt;

  /**
   * 删除者
   */
  @Field(nullable = true)
  private String deletedBy;

  /**
   * 删除时间
   */
  @Field(nullable = true)
  private LocalDateTime deletedAt;

  /**
   * 软删除状态
   */
  @JsonProperty("isDeleted")
  @Field(nullable = true, type = "Boolean")
  private boolean isDeleted;

  private List<? extends DataDictItem> items;

  public DataDictModel() {
  }

  public DataDictModel(DataDict dict) {
    setName(dict.getName());
    setTitle(dict.getTitle());
    setType(dict.getType());
    setGroup(dict.getGroup());
    setEditable(dict.isEditable());
    setTreeMode(dict.isTreeMode());
  }

  public DataDictModel(DataDictModel model) {
    setName(model.getName());
    setTitle(model.getTitle());
    setType(model.getType());
    setGroup(model.getGroup());
    setEditable(model.isEditable());
    setTreeMode(model.isTreeMode());
    setCreatedBy(model.getCreatedBy());
    setCreatedAt(model.getCreatedAt());
    setUpdatedBy(model.getUpdatedBy());
    setUpdatedAt(model.getUpdatedAt());
    setDeletedBy(model.getDeletedBy());
    setDeletedAt(model.getDeletedAt());
    setDeleted(model.isDeleted());
  }

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

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  public boolean isEditable() {
    return isEditable;
  }

  public void setEditable(boolean editable) {
    isEditable = editable;
  }

  public boolean isTreeMode() {
    return treeMode;
  }

  public void setTreeMode(boolean treeMode) {
    this.treeMode = treeMode;
  }

  public List<? extends DataDictItem> getItems() {
    return items;
  }

  public void setItems(List<? extends DataDictItem> items) {
    this.items = items;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getDeletedBy() {
    return deletedBy;
  }

  public void setDeletedBy(String deletedBy) {
    this.deletedBy = deletedBy;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  @JsonProperty("isDeleted")
  public boolean isDeleted() {
    return isDeleted;
  }

  public void setDeleted(boolean deleted) {
    isDeleted = deleted;
  }
}
