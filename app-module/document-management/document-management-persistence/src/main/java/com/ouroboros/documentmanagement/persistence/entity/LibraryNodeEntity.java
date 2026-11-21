package com.ouroboros.documentmanagement.persistence.entity;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

import java.time.LocalDateTime;

/**
 * 档案库节点实体（目录和文档）
 */
@Model(label = "档案库节点", rawName = "doc_library_node")
public class LibraryNodeEntity {

  @PrimaryKey(generator = "snowflake")
  @Field(label = "ID")
  private String id;

  @Field(label = "档案库ID", size = 100, nullable = false)
  private String libraryId;

  @Field(label = "父节点ID", size = 100, nullable = true)
  private String parentId;

  @Field(label = "名称", size = 255, nullable = false)
  private String name;

  @Field(label = "节点类型", size = 50, nullable = false)
  private String type;  // DIRECTORY or DOCUMENT

  // Document-specific fields
  @Field(label = "文件大小(字节)", nullable = true)
  private Long size;

  @Field(label = "MIME类型", size = 255, nullable = true)
  private String mimeType;

  @Field(label = "存储路径", size = 1000, nullable = true)
  private String storagePath;

  @Field(label = "创建者", size = 100, nullable = false)
  private String createdBy;

  @Field(label = "创建时间", nullable = false)
  private LocalDateTime createdAt;

  @Field(label = "更新者", size = 100)
  private String updatedBy;

  @Field(label = "更新时间")
  private LocalDateTime updatedAt;

  // Getters and Setters

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLibraryId() {
    return libraryId;
  }

  public void setLibraryId(String libraryId) {
    this.libraryId = libraryId;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public void setStoragePath(String storagePath) {
    this.storagePath = storagePath;
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
}
