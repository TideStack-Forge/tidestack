package com.ouroboros.documentmanagement.persistence.mapper;

import com.ouroboros.documentmanagement.model.DirectoryNode;
import com.ouroboros.documentmanagement.model.DirectoryNodeImpl;
import com.ouroboros.documentmanagement.model.DocumentNode;
import com.ouroboros.documentmanagement.model.DocumentNodeImpl;
import com.ouroboros.documentmanagement.model.LibraryNode;
import com.ouroboros.documentmanagement.model.NodeType;
import com.ouroboros.documentmanagement.persistence.entity.LibraryNodeEntity;

import java.util.ArrayList;

/**
 * 档案库节点域模型与实体转换器
 */
public class LibraryNodeMapper {

  /**
   * 实体转换为域模型
   */
  public LibraryNode toModel(LibraryNodeEntity entity) {
    if (entity == null) {
      return null;
    }

    String type = entity.getType();
    if ("DIRECTORY".equals(type)) {
      return DirectoryNode.builder()
          .id(entity.getId())
          .libraryId(entity.getLibraryId())
          .parentId(entity.getParentId())
          .name(entity.getName())
          .children(new ArrayList<>())
          .createdBy(entity.getCreatedBy())
          .createdAt(entity.getCreatedAt())
          .updatedBy(entity.getUpdatedBy())
          .updatedAt(entity.getUpdatedAt())
          .build();
    } else if ("DOCUMENT".equals(type)) {
      return DocumentNode.builder()
          .id(entity.getId())
          .libraryId(entity.getLibraryId())
          .parentId(entity.getParentId())
          .name(entity.getName())
          .size(entity.getSize() != null ? entity.getSize() : 0L)
          .mimeType(entity.getMimeType())
          .storagePath(entity.getStoragePath())
          .createdBy(entity.getCreatedBy())
          .createdAt(entity.getCreatedAt())
          .updatedBy(entity.getUpdatedBy())
          .updatedAt(entity.getUpdatedAt())
          .build();
    }

    throw new IllegalArgumentException("Unknown node type: " + type);
  }

  /**
   * 域模型转换为实体
   */
  public LibraryNodeEntity toEntity(LibraryNode model) {
    if (model == null) {
      return null;
    }

    LibraryNodeEntity entity = new LibraryNodeEntity();
    entity.setId(model.getId());
    entity.setLibraryId(model.getLibraryId());
    entity.setParentId(model.getParentId());
    entity.setName(model.getName());
    entity.setType(model.getType().name());
    entity.setCreatedBy(model.getCreatedBy());
    entity.setCreatedAt(model.getCreatedAt());
    entity.setUpdatedBy(model.getUpdatedBy());
    entity.setUpdatedAt(model.getUpdatedAt());

    // 如果是文档节点，设置文档特有字段
    if (model.getType() == NodeType.DOCUMENT && model instanceof DocumentNode) {
      DocumentNode doc = (DocumentNode) model;
      entity.setSize(doc.getSize());
      entity.setMimeType(doc.getMimeType());
      entity.setStoragePath(doc.getStoragePath());
    }

    return entity;
  }
}
