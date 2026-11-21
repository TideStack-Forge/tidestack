package com.ouroboros.documentmanagement.model;

import java.time.LocalDateTime;
import java.util.List;

import com.ouroboros.documentmanagement.model.acl.AclEntry;

/**
 * 档案库节点（目录或文档）
 */
public interface LibraryNode {
  /**
   * 获取节点ID
   */
  String getId();

  /**
   * 获取父目录ID（如果为null，表示是文档库的根目录）
   */
  String getParentId();

  /**
   * 获取节点类型
   */
  NodeType getType();

  /**
   * 获取节点名称
   */
  String getName();

  /**
   * 获取描述
   */
  String getDescription();

  /**
   * 获取文档库ID
   */
  String getLibraryId();

  /**
   * 获取创建人ID
   */
  String getCreatedBy();

  /**
   * 获取创建时间
   */
  LocalDateTime getCreatedAt();

  /**
   * 获取最后修改人ID
   */
  String getUpdatedBy();

  /**
   * 获取最后修改时间
   */
  LocalDateTime getUpdatedAt();

  /**
   * 获取ACL条目列表
   */
  List<AclEntry> getAclEntries();

  /**
   * 从当前对象创建 Builder（用于更新）
   */
  LibraryNodeBuilder toBuilder();

  /**
   * 节点 Builder 基础接口
   */
  interface LibraryNodeBuilder {
    LibraryNodeBuilder id(String id);
    LibraryNodeBuilder parentId(String parentId);
    LibraryNodeBuilder name(String name);
    LibraryNodeBuilder description(String description);
    LibraryNodeBuilder libraryId(String libraryId);
    LibraryNodeBuilder aclEntries(List<AclEntry> aclEntries);
    LibraryNodeBuilder addAclEntry(AclEntry aclEntry);
    LibraryNodeBuilder createdBy(String createdBy);
    LibraryNodeBuilder createdAt(LocalDateTime createdAt);
    LibraryNodeBuilder updatedBy(String updatedBy);
    LibraryNodeBuilder updatedAt(LocalDateTime updatedAt);
  }
}
