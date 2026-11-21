package com.ouroboros.documentmanagement.webapp.dto;

import com.ouroboros.documentmanagement.model.NodeType;

import java.time.LocalDateTime;

/**
 * 节点项 DTO，包含节点信息和用户权限
 */
public class NodeItemDTO {
  private String id;
  private String parentId;
  private NodeType type;
  private String name;
  private Long size;
  private String description;
  private String libraryId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;
  private String createdByDisplayName;
  private String updatedByDisplayName;

  private PermissionsDTO permissions;

  public NodeItemDTO() {}

  // Getters
  public String getId() {
    return id;
  }

  public String getParentId() {
    return parentId;
  }

  public NodeType getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getExtension() {
    if (type == NodeType.DIRECTORY) {
      return null;
    }
    if (name == null) {
      return null;
    }
    int index = name.lastIndexOf('.');
    if (index < 0) {
      return null;
    }
    return name.substring(index + 1);
  }

  public Long getSize() {
    return size;
  }

  public NodeItemDTO setSize(Long size) {
    this.size = size;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public String getLibraryId() {
    return libraryId;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public PermissionsDTO getPermissions() {
    return permissions;
  }

  public String getCreatedByDisplayName() {
    return createdByDisplayName;
  }

  public String getUpdatedByDisplayName() {
    return updatedByDisplayName;
  }

  // Fluent setters (chainable) with "set" prefix
  public NodeItemDTO setId(String id) {
    this.id = id;
    return this;
  }

  public NodeItemDTO setParentId(String parentId) {
    this.parentId = parentId;
    return this;
  }

  public NodeItemDTO setType(NodeType type) {
    this.type = type;
    return this;
  }

  public NodeItemDTO setName(String name) {
    this.name = name;
    return this;
  }

  public NodeItemDTO setDescription(String description) {
    this.description = description;
    return this;
  }

  public NodeItemDTO setLibraryId(String libraryId) {
    this.libraryId = libraryId;
    return this;
  }

  public NodeItemDTO setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
    return this;
  }

  public NodeItemDTO setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public NodeItemDTO setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
    return this;
  }

  public NodeItemDTO setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public NodeItemDTO setPermissions(PermissionsDTO permissions) {
    this.permissions = permissions;
    return this;
  }

  public NodeItemDTO setCreatedByDisplayName(String createdByDisplayName) {
    this.createdByDisplayName = createdByDisplayName;
    return this;
  }

  public NodeItemDTO setUpdatedByDisplayName(String updatedByDisplayName) {
    this.updatedByDisplayName = updatedByDisplayName;
    return this;
  }
}