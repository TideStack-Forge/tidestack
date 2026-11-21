package com.ouroboros.documentmanagement.webapp.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.model.acl.PrincipalResolver;
import com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider;

/**
 * 文档库后台管理 DTO，包含所有者显示名称
 */
public class DocumentLibraryAdminDTO {
  // 基本信息
  private String id;
  private String name;
  private String displayName;
  private String description;
  private String owner;
  private String ownerDisplayName; // 所有者显示名称
  private List<String> adminIds;

  // 配额限制
  private Long maxSizeLimit;
  private Long maxFileCountLimit;

  // 状态
  private boolean deleted;
  private boolean disabled;
  private boolean locked;
  private boolean trashEnabled;

  // 审计信息
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;

  public static DocumentLibraryAdminDTO fromDocumentLibrary(DocumentLibrary library) {
    if (library == null) {
      return null;
    }

    // 获取 USER 类型的 PrincipalResolver
    PrincipalResolver userResolver = PrincipalTypeProvider.get("USER")
        .map(principalType -> principalType.getResolver())
        .orElse(null);

    String ownerDisplayName = library.getOwner();
    if (userResolver != null) {
      ownerDisplayName = userResolver.getDisplayName(library.getOwner());
    }

    return new DocumentLibraryAdminDTO()
        .setId(library.getId())
        .setName(library.getName())
        .setDisplayName(library.getDisplayName())
        .setDescription(library.getDescription())
        .setOwner(library.getOwner())
        .setOwnerDisplayName(ownerDisplayName)
        .setAdminIds(library.getAdminIds())
        .setMaxSizeLimit(library.getMaxSizeLimit())
        .setMaxFileCountLimit(library.getMaxFileCountLimit())
        .setDeleted(library.isDeleted())
        .setDisabled(library.isDisabled())
        .setLocked(library.isLocked())
        .setTrashEnabled(library.isTrashEnabled())
        .setCreatedBy(library.getCreatedBy())
        .setCreatedAt(library.getCreatedAt())
        .setUpdatedBy(library.getUpdatedBy())
        .setUpdatedAt(library.getUpdatedAt());
  }

  public String getId() {
    return id;
  }

  public DocumentLibraryAdminDTO setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public DocumentLibraryAdminDTO setName(String name) {
    this.name = name;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public DocumentLibraryAdminDTO setDisplayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public DocumentLibraryAdminDTO setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getOwner() {
    return owner;
  }

  public DocumentLibraryAdminDTO setOwner(String owner) {
    this.owner = owner;
    return this;
  }

  public String getOwnerDisplayName() {
    return ownerDisplayName;
  }

  public DocumentLibraryAdminDTO setOwnerDisplayName(String ownerDisplayName) {
    this.ownerDisplayName = ownerDisplayName;
    return this;
  }

  public List<String> getAdminIds() {
    return adminIds;
  }

  public DocumentLibraryAdminDTO setAdminIds(List<String> adminIds) {
    this.adminIds = adminIds;
    return this;
  }

  public Long getMaxSizeLimit() {
    return maxSizeLimit;
  }

  public DocumentLibraryAdminDTO setMaxSizeLimit(Long maxSizeLimit) {
    this.maxSizeLimit = maxSizeLimit;
    return this;
  }

  public Long getMaxFileCountLimit() {
    return maxFileCountLimit;
  }

  public DocumentLibraryAdminDTO setMaxFileCountLimit(Long maxFileCountLimit) {
    this.maxFileCountLimit = maxFileCountLimit;
    return this;
  }

  public boolean isDeleted() {
    return deleted;
  }

  public DocumentLibraryAdminDTO setDeleted(boolean deleted) {
    this.deleted = deleted;
    return this;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public DocumentLibraryAdminDTO setDisabled(boolean disabled) {
    this.disabled = disabled;
    return this;
  }

  public boolean isLocked() {
    return locked;
  }

  public DocumentLibraryAdminDTO setLocked(boolean locked) {
    this.locked = locked;
    return this;
  }

  public boolean isTrashEnabled() {
    return trashEnabled;
  }

  public DocumentLibraryAdminDTO setTrashEnabled(boolean trashEnabled) {
    this.trashEnabled = trashEnabled;
    return this;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public DocumentLibraryAdminDTO setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
    return this;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public DocumentLibraryAdminDTO setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public DocumentLibraryAdminDTO setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
    return this;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public DocumentLibraryAdminDTO setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}