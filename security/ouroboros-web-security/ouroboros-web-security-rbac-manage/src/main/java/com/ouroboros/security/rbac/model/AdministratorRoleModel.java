package com.ouroboros.security.rbac.model;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.authority.AdministratorGrantedAuthorityModel;

public class AdministratorRoleModel implements Role {

  @Override
  public String getId() {
    return "administrator";
  }

  @Override
  public String getName() {
    return "超级管理员";
  }

  @Override
  public List<? extends OuroborosGrantedAuthority> getAuthorities() {
    return AuthorityCenter.getAllAuthority().values().stream()
        .map(AdministratorGrantedAuthorityModel::new)
        .collect(Collectors.toList());
  }

  @Override
  public boolean isBuiltinRole() {
    return false;
  }

  @Override
  public boolean canEditAuthorities() {
    return false;
  }

  @Override
  public boolean canDelete() {
    return false;
  }

  @Override
  public boolean canRename() {
    return false;
  }

  @Override
  public boolean canManageUsers() {
    return true;
  }
}
