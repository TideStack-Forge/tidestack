package com.ouroboros.documentmanagement.persistence.mapper;

import com.ouroboros.documentmanagement.model.DocumentLibrary;
import com.ouroboros.documentmanagement.model.DocumentLibraryImpl;
import com.ouroboros.documentmanagement.persistence.entity.DocumentLibraryEntity;

import java.util.ArrayList;

/**
 * 档案库域模型与实体转换器
 */
public class DocumentLibraryMapper {

  /**
   * 实体转换为域模型
   */
  public DocumentLibrary toModel(DocumentLibraryEntity entity) {
    if (entity == null) {
      return null;
    }

    return DocumentLibrary.builder()
        .id(entity.getId())
        .name(entity.getName())
        .displayName(entity.getDisplayName())
        .description(entity.getDescription())
        .owner(entity.getOwner())
        .adminIds(entity.getAdminIds() != null ? entity.getAdminIds() : new ArrayList<>())
        .maxSizeLimit(entity.getMaxSizeLimit())
        .maxFileCountLimit(entity.getMaxFileCountLimit())
        .deleted(entity.getDeleted() != null ? entity.getDeleted() : false)
        .disabled(entity.getDisabled() != null ? entity.getDisabled() : false)
        .locked(entity.getLocked() != null ? entity.getLocked() : false)
        .trashEnabled(entity.getTrashEnabled() != null ? entity.getTrashEnabled() : true)
        .createdBy(entity.getCreatedBy())
        .createdAt(entity.getCreatedAt())
        .updatedBy(entity.getUpdatedBy())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /**
   * 域模型转换为实体
   */
  public DocumentLibraryEntity toEntity(DocumentLibrary model) {
    if (model == null) {
      return null;
    }

    DocumentLibraryEntity entity = new DocumentLibraryEntity();
    entity.setId(model.getId());
    entity.setName(model.getName());
    entity.setDisplayName(model.getDisplayName());
    entity.setDescription(model.getDescription());
    entity.setOwner(model.getOwner());
    entity.setAdminIds(model.getAdminIds() != null ? new ArrayList<>(model.getAdminIds()) : new ArrayList<>());
    entity.setMaxSizeLimit(model.getMaxSizeLimit());
    entity.setMaxFileCountLimit(model.getMaxFileCountLimit());
    entity.setDeleted(model.isDeleted());
    entity.setDisabled(model.isDisabled());
    entity.setLocked(model.isLocked());
    entity.setTrashEnabled(model.isTrashEnabled());
    entity.setCreatedBy(model.getCreatedBy());
    entity.setCreatedAt(model.getCreatedAt());
    entity.setUpdatedBy(model.getUpdatedBy());
    entity.setUpdatedAt(model.getUpdatedAt());
    return entity;
  }
}
