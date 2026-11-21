package com.ouroboros.appmodule.model;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;

public class PlacementModuleAuthority implements Authority {
  @JsonIgnore
  private final AppModule module;

  public PlacementModuleAuthority(AppModule module) {
    this.module = module;
  }

  public String getDescription() {
    return module.getDescription();
  }

  public String getIcon() {
    return module.getIcon();
  }

  @Override
  public String getTitle() {
    return module.getTitle();
  }

  @Override
  public AccessControlDefine getAccessControlDefine() {
    return AccessControlDefine.empty();
  }

  @Override
  public List<String> getApis() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getUiViews() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getMenuItems() {
    return Collections.singletonList(getFullName());
  }

  @Override
  public String getName() {
    return module.getName();
  }

  @Override
  public String getParent() {
    return module.getParent();
  }

  @Override
  public String getFullName() {
    return module.getFullName();
  }

  @Override
  public Integer getOrder() {
    return module.getOrder();
  }
}
