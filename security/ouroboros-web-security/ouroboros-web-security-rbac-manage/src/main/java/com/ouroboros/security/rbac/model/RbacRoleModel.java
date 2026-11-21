package com.ouroboros.security.rbac.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.authority.RbacGrantedAuthorityModel;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.entity.RoleEntity;

public class RbacRoleModel implements Role {
  private final RoleEntity entity;
  private final List<RoleAuthorityMapEntity> authorityEntities;
  private List<? extends OuroborosGrantedAuthority> authorities;

  public RbacRoleModel(RoleEntity entity, List<RoleAuthorityMapEntity> authorityEntities) {
    this.entity = entity;
    this.authorityEntities = authorityEntities;
  }

  @Override
  public String getId() {
    return entity.getId();
  }

  @Override
  public String getName() {
    return entity.getName();
  }

  @Override
  public List<? extends OuroborosGrantedAuthority> getAuthorities() {
    if (authorities == null) {
      authorities = authorityEntities.stream()
          .map(RbacGrantedAuthorityModel::new)
          .collect(Collectors.toList());
    }
    return authorities;
  }

  @Override
  public boolean canEditAuthorities() {
    return true;
  }

  @Override
  public boolean canDelete() {
    return true;
  }

  @Override
  public boolean canRename() {
    return true;
  }

  @Override
  public boolean canManageUsers() {
    return true;
  }

  public LocalDateTime getCreatedAt() {
    return entity.getCreatedAt();
  }
}
