package com.ouroboros.appmodule.model;

import java.util.Collections;
import java.util.List;

import com.ouroboros.appmodule.AppModule;
import com.ouroboros.model.annotation.ProCodeModel;
import com.ouroboros.menu.MenuModel;

@ProCodeModel
public class MenuModelAppModule implements AppModule {
  private final MenuModel menuModel;

  public MenuModelAppModule(MenuModel menuModel) {
    this.menuModel = menuModel;
  }

  @Override
  public String getFullName() {
    return menuModel.getFullName();
  }

  @Override
  public String getTitle() {
    return menuModel.getTitle();
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public String getIcon() {
    return menuModel.getIcon();
  }

  @Override
  public Integer getOrder() {
    return menuModel.getOrder();
  }

  @Override
  public String getMenu() {
    return menuModel.getFullName();
  }

  @Override
  public List<String> getSubMenus() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getAuthorities() {
    return Collections.emptyList();
  }
}
