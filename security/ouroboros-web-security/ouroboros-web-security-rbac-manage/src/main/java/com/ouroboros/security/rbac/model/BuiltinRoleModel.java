package com.ouroboros.security.rbac.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.rbac.Role;

public class BuiltinRoleModel implements Role {

  private String id;
  private String name;
  private List<BuiltinRoleAuthorityModel> authorities = new ArrayList<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @JsonIgnore
  public List<BuiltinRoleAuthorityModel> getAuthorityDefinitions() {
    return Optional.ofNullable(authorities).orElse(Collections.<BuiltinRoleAuthorityModel>emptyList());
  }

  public void setAuthorities(List<BuiltinRoleAuthorityModel> authorities) {
    this.authorities = authorities == null ? new ArrayList<BuiltinRoleAuthorityModel>() : new ArrayList<BuiltinRoleAuthorityModel>(authorities);
  }

  @Override
  public List<? extends OuroborosGrantedAuthority> getAuthorities() {
    return getAuthorityDefinitions().stream().collect(Collectors.toList());
  }

  @Override
  public boolean isBuiltinRole() {
    return true;
  }

  @Override
  public boolean canEditAuthorities() {
    return true;
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
