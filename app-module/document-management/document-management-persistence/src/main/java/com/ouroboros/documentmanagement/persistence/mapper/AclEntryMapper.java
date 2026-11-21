package com.ouroboros.documentmanagement.persistence.mapper;

import com.ouroboros.documentmanagement.model.acl.AclEntry;
import com.ouroboros.documentmanagement.model.acl.Permission;
import com.ouroboros.documentmanagement.model.acl.PermissionSet;
import com.ouroboros.documentmanagement.model.acl.ResourceType;
import com.ouroboros.documentmanagement.persistence.entity.AclEntryEntity;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 权限条目域模型与实体转换器
 */
public class AclEntryMapper {

  /**
   * 实体转换为域模型
   */
  public AclEntry toModel(AclEntryEntity entity) {
    if (entity == null) {
      return null;
    }

    // 将权限名称列表转换为 PermissionSet
    PermissionSet permissions = PermissionSet.empty();
    if (entity.getPermissions() != null) {
      for (String permName : entity.getPermissions()) {
        Optional<Permission> permission = findPermission(permName);
        if (permission.isPresent()) {
          permissions = permissions.add(permission.get());
        }
      }
    }

    return AclEntry.builder()
        .id(entity.getId())
        .resourceId(entity.getResourceId())
        .resourceType(ResourceType.valueOf(entity.getResourceType()))
        .libraryId(entity.getLibraryId())
        .principal(entity.getPrincipal())
        .principalType(entity.getPrincipalType())
        .permissions(permissions)
        .build();
  }

  /**
   * 域模型转换为实体
   */
  public AclEntryEntity toEntity(AclEntry model) {
    if (model == null) {
      return null;
    }

    AclEntryEntity entity = new AclEntryEntity();
    entity.setId(model.getId());
    entity.setResourceId(model.getResourceId());
    entity.setResourceType(model.getResourceType().name());
    entity.setLibraryId(model.getLibraryId());
    entity.setPrincipal(model.getPrincipal());
    entity.setPrincipalType(model.getPrincipalType());

    // 将 PermissionSet 转换为权限名称列表
    if (model.getPermissions() != null) {
      entity.setPermissions(
          model.getPermissions().getPermissions().stream()
              .map(Permission::name)
              .collect(Collectors.toList())
      );
    } else {
      entity.setPermissions(new ArrayList<>());
    }

    return entity;
  }

  private Optional<Permission> findPermission(String name) {
    for (Permission permission : Permission.values()) {
      if (permission.name().equals(name)) {
        return Optional.of(permission);
      }
    }
    return Optional.empty();
  }
}
