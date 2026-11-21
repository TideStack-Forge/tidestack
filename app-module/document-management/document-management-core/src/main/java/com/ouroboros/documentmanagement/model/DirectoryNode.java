package com.ouroboros.documentmanagement.model;

import java.util.List;

/**
 * 目录元数据接口
 */
public interface DirectoryNode extends LibraryNode {
  /**
   * 获取子目录列表
   */
  List<LibraryNode> getChildren();

  /**
   * 判断是否为根目录
   */
  default boolean isRoot() {
    return getParentId() == null;
  }

  /**
   * 创建 Builder
   */
  static Builder builder() {
    return DirectoryNodeImpl.builder();
  }

  static Builder builder(DirectoryNode node) {
    return DirectoryNodeImpl.builder(node);
  }

  @Override
  Builder toBuilder();

  /**
   * 目录 Builder 接口
   */
  interface Builder extends LibraryNodeBuilder {
    @Override
    Builder id(String id);
    @Override
    Builder parentId(String parentId);
    @Override
    Builder name(String name);
    @Override
    Builder description(String description);
    @Override
    Builder libraryId(String libraryId);
    @Override
    Builder aclEntries(List<com.ouroboros.documentmanagement.model.acl.AclEntry> aclEntries);
    @Override
    Builder addAclEntry(com.ouroboros.documentmanagement.model.acl.AclEntry aclEntry);
    @Override
    Builder createdBy(String createdBy);
    @Override
    Builder createdAt(java.time.LocalDateTime createdAt);
    @Override
    Builder updatedBy(String updatedBy);
    @Override
    Builder updatedAt(java.time.LocalDateTime updatedAt);

    Builder children(List<LibraryNode> children);
    Builder addChild(LibraryNode child);

    DirectoryNode build();
  }
}
