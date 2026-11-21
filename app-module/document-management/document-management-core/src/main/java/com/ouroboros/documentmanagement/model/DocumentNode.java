package com.ouroboros.documentmanagement.model;

import java.util.List;

/**
 * 文档元数据接口
 */
public interface DocumentNode extends LibraryNode {
  /**
   * 获取文档大小（字节）
   */
  Long getSize();

  /**
   * 获取文档MIME类型
   */
  String getMimeType();

  /**
   * 获取文件扩展名
   */
  default String getExtension() {
    String name = getName();
    if (name == null) {
      return "";
    }
    if (name.contains(".")) {
      return name.substring(name.lastIndexOf(".") + 1);
    }
    return "";
  }

  /**
   * 获取存储路径（实际文件存储位置）
   */
  String getStoragePath();

  /**
   * 创建 Builder
   */
  static Builder builder() {
    return DocumentNodeImpl.builder();
  }

  static Builder builder(DocumentNode node) {
    return DocumentNodeImpl.builder(node);
  }

  @Override
  Builder toBuilder();

  /**
   * 文档 Builder 接口
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

    Builder size(Long size);
    Builder mimeType(String mimeType);
    Builder storagePath(String storagePath);

    DocumentNode build();
  }
}
