package com.ouroboros.documentmanagement.model;

import com.ouroboros.documentmanagement.model.acl.AclEntry;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 目录节点实现类（不可变对象）
 */
public class DirectoryNodeImpl implements DirectoryNode {

  private final String id;
  private final String parentId;
  private final String name;
  private final String description;
  private final String libraryId;
  private final List<LibraryNode> children;
  private final List<AclEntry> aclEntries;
  private final String createdBy;
  private final LocalDateTime createdAt;
  private final String updatedBy;
  private final LocalDateTime updatedAt;

  private DirectoryNodeImpl(Builder builder) {
    this.id = builder.id;
    this.parentId = builder.parentId;
    this.name = builder.name;
    this.description = builder.description;
    this.libraryId = builder.libraryId;
    this.children = Collections.unmodifiableList(new ArrayList<>(builder.children));
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
    return NodeType.DIRECTORY;
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
  public List<LibraryNode> getChildren() {
    return children;
  }

  /**
   * 创建 Builder
   */
  public static DirectoryNode.Builder builder() {
    return new Builder();
  }

  public static DirectoryNode.Builder builder(DirectoryNode directory) {
    return new Builder(directory);
  }

  /**
   * 从现有对象创建 Builder（用于更新）
   */
  @Override
  public DirectoryNode.Builder toBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DirectoryNodeImpl that = (DirectoryNodeImpl) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "DirectoryNodeImpl{" +
        "id='" + id + '\'' +
        ", name='" + name + '\'' +
        ", libraryId='" + libraryId + '\'' +
        ", parentId='" + parentId + '\'' +
        ", isRoot=" + isRoot() +
        ", childrenCount=" + children.size() +
        '}';
  }

  /**
   * 目录节点 Builder 实现
   */
  public static class Builder implements DirectoryNode.Builder {
    private String id;
    private String parentId;
    private String name;
    private String description;
    private String libraryId;
    private List<LibraryNode> children = new ArrayList<>();
    private List<AclEntry> aclEntries = new ArrayList<>();
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    private Builder() {}

    private Builder(DirectoryNodeImpl directory) {
      this.id = directory.id;
      this.parentId = directory.parentId;
      this.name = directory.name;
      this.description = directory.description;
      this.libraryId = directory.libraryId;
      this.children = new ArrayList<>(directory.children);
      this.aclEntries = new ArrayList<>(directory.aclEntries);
      this.createdBy = directory.createdBy;
      this.createdAt = directory.createdAt;
      this.updatedBy = directory.updatedBy;
      this.updatedAt = directory.updatedAt;
    }

    private Builder(DirectoryNode directory) {
      this.id = directory.getId();
      this.parentId = directory.getParentId();
      this.name = directory.getName();
      this.description = directory.getDescription();
      this.libraryId = directory.getLibraryId();
      this.children = directory.getChildren();
      this.aclEntries = directory.getAclEntries();
      this.createdBy = directory.getCreatedBy();
      this.createdAt = directory.getCreatedAt();
      this.updatedBy = directory.getUpdatedBy();
      this.updatedAt = directory.getUpdatedAt();
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

    public Builder children(List<LibraryNode> children) {
      this.children = new ArrayList<>(children);
      return this;
    }

    public Builder addChild(LibraryNode child) {
      this.children.add(child);
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
     * 构建 DirectoryNode 对象
     */
    @Override
    public DirectoryNode build() {
      // 基本验证
      Objects.requireNonNull(name, "name 不能为 null");
      Objects.requireNonNull(libraryId, "libraryId 不能为 null");
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

      return new DirectoryNodeImpl(this);
    }
  }
}
