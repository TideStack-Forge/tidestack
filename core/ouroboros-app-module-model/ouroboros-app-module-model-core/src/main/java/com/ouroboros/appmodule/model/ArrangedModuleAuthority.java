package com.ouroboros.appmodule.model;

import java.beans.Transient;
import java.util.List;
import java.util.Objects;

import com.ouroboros.appmodule.AppModule;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;

public class ArrangedModuleAuthority implements Authority {
  private final Authority authority;
  private final AppModule module;

  public ArrangedModuleAuthority(Authority authority, AppModule module) {
    this.authority = authority;
    this.module = module;
  }

  @Override
  public String getFullName() {
    return authority.getFullName();
  }

  @Transient
  @Override
  public String getName() {
    return authority.getName();
  }

  @Transient
  @Override
  public Integer getLevel() {
    return authority.getLevel();
  }

  @Override
  public String getTitle() {
    if (Objects.equals(authority.getFullName(), module.getFullName())) {
      return module.getTitle();
    }
    return authority.getTitle();
  }

  @Override
  public AccessControlDefine getAccessControlDefine() {
    return authority.getAccessControlDefine();
  }

  @Override
  public List<String> getApis() {
    return authority.getApis();
  }

  @Override
  public List<String> getUiViews() {
    return authority.getUiViews();
  }

  @Override
  public List<String> getMenuItems() {
    return authority.getMenuItems();
  }

  @Override
  public Integer getOrder() {
    var moduleOrder = module.getOrder();
    var authorityOrder = authority.getOrder();
    return (moduleOrder == null ? 0 : moduleOrder) + (authorityOrder == null ? 0 : authorityOrder);
  }

  @Override
  public String getParent() {
    if (Objects.equals(authority.getFullName(), module.getFullName())) {
      return module.getParent();
    }
    return authority.getParent();
  }
}
