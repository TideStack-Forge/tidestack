package com.ouroboros.documentmanagement.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档库
 */
public interface DocumentLibrary {
  /**
   * 文档库ID
   */
  String getId();

  /**
   * 文档库名称
   */
  String getName();

  /**
   * 文档库显示名称
   */
  String getDisplayName();

  /**
   * 文档库描述
   */
  String getDescription();

  /**
   * 文档库拥有者ID
   */
  String getOwner();

  /**
   * 文档库管理员ID集合
   */
  List<String> getAdminIds();

  /**
   * 文档库最大大小限制
   */
  Long getMaxSizeLimit();

  /**
   * 文档库最大文件数量限制
   */
  Long getMaxFileCountLimit();

  /**
   * 文档库是否被删除
   */
  boolean isDeleted();

  /**
   * 文档库是否被禁用
   */
  boolean isDisabled();

  /**
   * 文档库是否被锁定
   */
  boolean isLocked();

  /**
   * 文档库是否启用回收站
   */
  boolean isTrashEnabled();

  /**
   * 文档库创建者ID
   */
  String getCreatedBy();

  /**
   * 文档库创建时间
   */
  LocalDateTime getCreatedAt();

  /**
   * 文档库更新者ID
   */
  String getUpdatedBy();

  /**
   * 文档库更新时间
   */
  LocalDateTime getUpdatedAt();

  /**
   * 创建 Builder
   */
  static Builder builder() {
    return DocumentLibraryImpl.builder();
  }

  static Builder builder(DocumentLibrary library) {
    return DocumentLibraryImpl.builder(library);
  }

  /**
   * 从当前对象创建 Builder（用于更新）
   */
  Builder toBuilder();

  /**
   * 文档库 Builder 接口
   */
  interface Builder {
    Builder id(String id);
    Builder name(String name);
    Builder displayName(String displayName);
    Builder description(String description);
    Builder owner(String owner);
    Builder adminIds(List<String> adminIds);
    Builder addAdmin(String adminId);
    Builder removeAdmin(String adminId);
    Builder maxSizeLimit(Long maxSizeLimit);
    Builder maxFileCountLimit(Long maxFileCountLimit);
    Builder deleted(boolean deleted);
    Builder disabled(boolean disabled);
    Builder locked(boolean locked);
    Builder trashEnabled(boolean trashEnabled);
    Builder createdBy(String createdBy);
    Builder createdAt(LocalDateTime createdAt);
    Builder updatedBy(String updatedBy);
    Builder updatedAt(LocalDateTime updatedAt);
    DocumentLibrary build();
  }
}
