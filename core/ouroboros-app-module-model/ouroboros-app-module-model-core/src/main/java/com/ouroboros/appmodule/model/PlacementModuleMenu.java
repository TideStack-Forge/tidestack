package com.ouroboros.appmodule.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelShowModeEnum;

public class PlacementModuleMenu implements MenuModel {
  @JsonIgnore
  private final AppModule module;

  public PlacementModuleMenu(AppModule module) {
    this.module = module;
  }

  @Override
  public Integer getOrder() {
    return module.getOrder();
  }

  @Override
  public String getIcon() {
    return module.getIcon();
  }

  @Override
  public String getTitle() {
    return module.getTitle();
  }

  @Override
  public MenuModelShowModeEnum getShowMode() {
    return null;
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
  public String getUrl() {
    return null;
  }

  @Override
  public String getFullName() {
    return module.getFullName();
  }
}
