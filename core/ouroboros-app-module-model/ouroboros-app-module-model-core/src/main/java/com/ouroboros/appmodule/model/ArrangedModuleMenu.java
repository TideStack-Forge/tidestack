package com.ouroboros.appmodule.model;

import java.beans.Transient;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelShowModeEnum;

public class ArrangedModuleMenu implements MenuModel {
  @JsonIgnore
  private final MenuModel menu;
  @JsonIgnore
  private final AppModule module;

  public ArrangedModuleMenu(MenuModel menu, AppModule module) {
    this.menu = menu;
    this.module = module;
  }

  @Override
  public String getUrl() {
    return menu.getUrl();
  }

  @Override
  public String getFullName() {
    return menu.getFullName();
  }

  @Transient
  @Override
  public String getName() {
    return menu.getName();
  }

  @Override
  public String getIcon() {
    return Optional.ofNullable(module.getIcon())
        .filter(StringUtils::isNotEmpty)
        .orElseGet(menu::getIcon);
  }

  @Override
  public String getTitle() {
    return module.getTitle();
  }

  @Override
  public MenuModelShowModeEnum getShowMode() {
    return menu.getShowMode();
  }

  @Override
  public Integer getOrder() {
    return Optional.ofNullable(module.getOrder())
        .orElseGet(menu::getOrder);
  }

  @Override
  public MenuScope getScope() {
    return menu.getScope();
  }

  @Override
  public String getParent() {
    return module.getParent();
  }
}
