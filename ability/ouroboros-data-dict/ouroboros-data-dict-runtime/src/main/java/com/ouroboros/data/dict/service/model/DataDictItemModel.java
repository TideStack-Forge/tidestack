package com.ouroboros.data.dict.service.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.data.annotation.AuditFields;
import com.ouroboros.data.annotation.LogicalDelete;

/**
 * @author liansz
 */
@Model(rawName = "t_sys_dictionary_item", label = "数据字典项")
@AuditFields
@LogicalDelete
public class DataDictItemModel implements Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field(nullable = true)
  private String parent;

  @Field
  private String code;

  @Field
  private String label;

  @Field
  private String dict;

  @Field(nullable = true)
  private Map<String, Object> extraProps;

  @Field(type = "Integer")
  private int order;

  @Field(nullable = true)
  private LocalDateTime createdAt;

  @Field(nullable = true)
  private String createdBy;

  @Field(nullable = true)
  private LocalDateTime updatedAt;

  @Field(nullable = true)
  private String updatedBy;

  @Field(nullable = true)
  private LocalDateTime deletedAt;

  @Field(nullable = true)
  private String deletedBy;

  @JsonProperty("isDeleted")
  @Field(nullable = true, type = "Boolean")
  private boolean isDeleted;

  public DataDictItemModel() {
  }

  public DataDictItemModel(DataDictItem item) {
    setDict(item.getDict());
    setCode(item.getCode());
    setLabel(item.getLabel());
    setOrder(item.getOrder());
    setExtraProps(item.getExtraProps());
  }

  public DataDictItemModel(DataDictItemModel model) {
    setId(model.getId());
    setParent(model.getParent());
    setCode(model.getCode());
    setLabel(model.getLabel());
    setDict(model.getDict());
    setExtraProps(model.getExtraProps());
    setOrder(model.getOrder());
    setCreatedAt(model.getCreatedAt());
    setCreatedBy(model.getCreatedBy());
    setUpdatedAt(model.getUpdatedAt());
    setUpdatedBy(model.getUpdatedBy());
    setDeletedAt(model.getDeletedAt());
    setDeletedBy(model.getDeletedBy());
    setDeleted(model.isDeleted());
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getDict() {
    return dict;
  }

  public void setDict(String dict) {
    this.dict = dict;
  }

  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  public void setExtraProps(Map<String, Object> extraProps) {
    this.extraProps = extraProps;
  }

  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  public String getDeletedBy() {
    return deletedBy;
  }

  public void setDeletedBy(String deletedBy) {
    this.deletedBy = deletedBy;
  }

  @JsonProperty("isDeleted")
  public boolean isDeleted() {
    return isDeleted;
  }

  public void setDeleted(boolean deleted) {
    isDeleted = deleted;
  }
}
