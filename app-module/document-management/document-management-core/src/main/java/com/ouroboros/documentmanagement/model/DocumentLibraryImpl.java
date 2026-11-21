package com.ouroboros.documentmanagement.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 文档库实现类（不可变对象）
 */
public class DocumentLibraryImpl implements DocumentLibrary {

  private final String id;
  private final String name;
  private final String displayName;
  private final String description;
  private final String owner;
  private final List<String> adminIds;
  private final Long maxSizeLimit;
  private final Long maxFileCountLimit;
  private final boolean deleted;
  private final boolean disabled;
  private final boolean locked;
  private final boolean trashEnabled;
  private final String createdBy;
  private final LocalDateTime createdAt;
  private final String updatedBy;
  private final LocalDateTime updatedAt;

  private DocumentLibraryImpl(Builder builder) {
    this.id = builder.id;
    this.name = builder.name;
    this.displayName = builder.displayName;
    this.description = builder.description;
    this.owner = builder.owner;
    this.adminIds = Collections.unmodifiableList(builder.adminIds);
    this.maxSizeLimit = builder.maxSizeLimit;
    this.maxFileCountLimit = builder.maxFileCountLimit;
    this.deleted = builder.deleted;
    this.disabled = builder.disabled;
    this.locked = builder.locked;
    this.trashEnabled = builder.trashEnabled;
    this.createdBy = builder.createdBy;
    this.createdAt = builder.createdAt;
    this.updatedBy = builder.updatedBy;
    this.updatedAt = builder.updatedAt;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getOwner() {
    return owner;
  }

  @Override
  public List<String> getAdminIds() {
    return adminIds;
  }

  @Override
  public Long getMaxSizeLimit() {
    return maxSizeLimit;
  }

  @Override
  public Long getMaxFileCountLimit() {
    return maxFileCountLimit;
  }

  @Override
  public boolean isDeleted() {
    return deleted;
  }

  @Override
  public boolean isDisabled() {
    return disabled;
  }

  @Override
  public boolean isLocked() {
    return locked;
  }

  @Override
  public boolean isTrashEnabled() {
    return trashEnabled;
  }

  @Override
  public String getCreatedBy() {
    return createdBy;
  }

  @Override
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  @Override
  public String getUpdatedBy() {
    return updatedBy;
  }

  @Override
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /**
   * 创建 Builder
   */
  public static DocumentLibrary.Builder builder() {
    return new Builder();
  }

  public static DocumentLibrary.Builder builder(DocumentLibrary library) {
    return new Builder(library);
  }

  /**
   * 从现有对象创建 Builder（用于更新）
   */
  @Override
  public DocumentLibrary.Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DocumentLibraryImpl that = (DocumentLibraryImpl) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "DocumentLibraryImpl{" +
        "id='" + id + '\'' +
        ", name='" + name + '\'' +
        ", displayName='" + displayName + '\'' +
        ", owner='" + owner + '\'' +
        ", deleted=" + deleted +
        ", disabled=" + disabled +
        ", locked=" + locked +
        '}';
  }

  /**
   * 文档库 Builder 实现
   */
  public static class Builder implements DocumentLibrary.Builder {
    private String id;
    private String name;
    private String displayName;
    private String description;
    private String owner;
    private List<String> adminIds = new ArrayList<>();
    private Long maxSizeLimit;
    private Long maxFileCountLimit;
    private boolean deleted = false;
    private boolean disabled = false;
    private boolean locked = false;
    private boolean trashEnabled = true;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    private Builder() {}

    private Builder(DocumentLibraryImpl library) {
      this.id = library.id;
      this.name = library.name;
      this.displayName = library.displayName;
      this.description = library.description;
      this.owner = library.owner;
      this.adminIds = new ArrayList<>(library.adminIds);
      this.maxSizeLimit = library.maxSizeLimit;
      this.maxFileCountLimit = library.maxFileCountLimit;
      this.deleted = library.deleted;
      this.disabled = library.disabled;
      this.locked = library.locked;
      this.trashEnabled = library.trashEnabled;
      this.createdBy = library.createdBy;
      this.createdAt = library.createdAt;
      this.updatedBy = library.updatedBy;
      this.updatedAt = library.updatedAt;
    }

    public Builder(DocumentLibrary library) {
      this.id = library.getId();
      this.name = library.getName();
      this.displayName = library.getDisplayName();
      this.description = library.getDescription();
      this.owner = library.getOwner();
      this.adminIds = new ArrayList<>(library.getAdminIds());
      this.maxSizeLimit = library.getMaxSizeLimit();
      this.maxFileCountLimit = library.getMaxFileCountLimit();
      this.deleted = library.isDeleted();
      this.disabled = library.isDisabled();
      this.locked = library.isLocked();
      this.trashEnabled = library.isTrashEnabled();
      this.createdBy = library.getCreatedBy();
      this.createdAt = library.getCreatedAt();
      this.updatedBy = library.getUpdatedBy();
      this.updatedAt = library.getUpdatedAt();
    }

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder owner(String owner) {
      this.owner = owner;
      return this;
    }

    public Builder adminIds(List<String> adminIds) {
      this.adminIds = new ArrayList<>(adminIds);
      return this;
    }

    public Builder addAdmin(String adminId) {
      this.adminIds.add(adminId);
      return this;
    }

    public Builder removeAdmin(String adminId) {
      this.adminIds.remove(adminId);
      return this;
    }

    public Builder maxSizeLimit(Long maxSizeLimit) {
      this.maxSizeLimit = maxSizeLimit;
      return this;
    }

    public Builder maxFileCountLimit(Long maxFileCountLimit) {
      this.maxFileCountLimit = maxFileCountLimit;
      return this;
    }

    public Builder deleted(boolean deleted) {
      this.deleted = deleted;
      return this;
    }

    public Builder disabled(boolean disabled) {
      this.disabled = disabled;
      return this;
    }

    public Builder locked(boolean locked) {
      this.locked = locked;
      return this;
    }

    public Builder trashEnabled(boolean trashEnabled) {
      this.trashEnabled = trashEnabled;
      return this;
    }

    public Builder createdBy(String createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder createdAt(LocalDateTime createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder updatedBy(String updatedBy) {
      this.updatedBy = updatedBy;
      return this;
    }

    public Builder updatedAt(LocalDateTime updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    /**
     * 构建 DocumentLibrary 对象
     */
    @Override
    public DocumentLibrary build() {
      // 基本验证
      Objects.requireNonNull(name, "name 不能为 null");
      Objects.requireNonNull(owner, "owner 不能为 null");

      // 设置默认值
      if (createdAt == null) {
        createdAt = LocalDateTime.now();
      }
      if (updatedAt == null) {
        updatedAt = createdAt;
      }
      if (createdBy == null) {
        createdBy = owner;
      }
      if (updatedBy == null) {
        updatedBy = owner;
      }
      if (displayName == null) {
        displayName = name;
      }

      return new DocumentLibraryImpl(this);
    }
  }
}
