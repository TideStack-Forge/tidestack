package com.ouroboros.documentmanagement.model;

import com.ouroboros.documentmanagement.model.acl.AclEntry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 文档节点实现类（不可变对象）
 */
public class DocumentNodeImpl implements DocumentNode {

  private final String id;
  private final String parentId;
  private final String name;
  private final String description;
  private final String libraryId;
  private final Long size;
  private final String mimeType;
  private final String storagePath;
  private final List<AclEntry> aclEntries;
  private final String createdBy;
  private final LocalDateTime createdAt;
  private final String updatedBy;
  private final LocalDateTime updatedAt;

  private DocumentNodeImpl(Builder builder) {
    this.id = builder.id;
    this.parentId = builder.parentId;
    this.name = builder.name;
    this.description = builder.description;
    this.libraryId = builder.libraryId;
    this.size = builder.size;
    this.mimeType = builder.mimeType;
    this.storagePath = builder.storagePath;
    this.aclEntries = Collections.unmodifiableList(new ArrayList<>(builder.aclEntries));
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
  public String getParentId() {
    return parentId;
  }

  @Override
  public NodeType getType() {
    return NodeType.DOCUMENT;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getLibraryId() {
    return libraryId;
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

  @Override
  public List<AclEntry> getAclEntries() {
    return aclEntries;
  }

  @Override
  public Long getSize() {
    return size;
  }

  @Override
  public String getMimeType() {
    return mimeType;
  }

  @Override
  public String getStoragePath() {
    return storagePath;
  }

  /**
   * 创建 Builder
   */
  public static DocumentNode.Builder builder() {
    return new Builder();
  }

  public static DocumentNode.Builder builder(DocumentNode document) {
    return new Builder(document);
  }

  /**
   * 从现有对象创建 Builder（用于更新）
   */
  @Override
  public DocumentNode.Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DocumentNodeImpl that = (DocumentNodeImpl) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "DocumentNodeImpl{" +
        "id='" + id + '\'' +
        ", name='" + name + '\'' +
        ", libraryId='" + libraryId + '\'' +
        ", parentId='" + parentId + '\'' +
        ", size=" + size +
        ", mimeType='" + mimeType + '\'' +
        ", extension='" + getExtension() + '\'' +
        '}';
  }

  /**
   * 文档节点 Builder 实现
   */
  public static class Builder implements DocumentNode.Builder {
    private String id;
    private String parentId;
    private String name;
    private String description;
    private String libraryId;
    private Long size;
    private String mimeType;
    private String storagePath;
    private List<AclEntry> aclEntries = new ArrayList<>();
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    private Builder() {}

    private Builder(DocumentNodeImpl document) {
      this.id = document.id;
      this.parentId = document.parentId;
      this.name = document.name;
      this.description = document.description;
      this.libraryId = document.libraryId;
      this.size = document.size;
      this.mimeType = document.mimeType;
      this.storagePath = document.storagePath;
      this.aclEntries = new ArrayList<>(document.aclEntries);
      this.createdBy = document.createdBy;
      this.createdAt = document.createdAt;
      this.updatedBy = document.updatedBy;
      this.updatedAt = document.updatedAt;
    }

    private Builder(DocumentNode document) {
      this.id = document.getId();
      this.parentId = document.getParentId();
      this.name = document.getName();
      this.description = document.getDescription();
      this.libraryId = document.getLibraryId();
      this.size = document.getSize();
      this.mimeType = document.getMimeType();
      this.storagePath = document.getStoragePath();
      this.aclEntries = document.getAclEntries();
      this.createdBy = document.getCreatedBy();
      this.createdAt = document.getCreatedAt();
      this.updatedBy = document.getUpdatedBy();
      this.updatedAt = document.getUpdatedAt();
    }

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder parentId(String parentId) {
      this.parentId = parentId;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder libraryId(String libraryId) {
      this.libraryId = libraryId;
      return this;
    }

    public Builder size(Long size) {
      this.size = size;
      return this;
    }

    public Builder mimeType(String mimeType) {
      this.mimeType = mimeType;
      return this;
    }

    public Builder storagePath(String storagePath) {
      this.storagePath = storagePath;
      return this;
    }

    public Builder aclEntries(List<AclEntry> aclEntries) {
      this.aclEntries = new ArrayList<>(aclEntries);
      return this;
    }

    public Builder addAclEntry(AclEntry aclEntry) {
      this.aclEntries.add(aclEntry);
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
     * 构建 DocumentNode 对象
     */
    @Override
    public DocumentNode build() {
      // 基本验证
      Objects.requireNonNull(name, "name 不能为 null");
      Objects.requireNonNull(libraryId, "libraryId 不能为 null");
      Objects.requireNonNull(parentId, "parentId 不能为 null");
      Objects.requireNonNull(storagePath, "storagePath 不能为 null");
      Objects.requireNonNull(createdBy, "createdBy 不能为 null");

      // 设置默认值
      if (createdAt == null) {
        createdAt = LocalDateTime.now();
      }
      if (updatedAt == null) {
        updatedAt = createdAt;
      }
      if (updatedBy == null) {
        updatedBy = createdBy;
      }
      if (size == null) {
        size = 0L;
      }

      return new DocumentNodeImpl(this);
    }
  }
}
