package com.ouroboros.appmodule.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.menu.MenuModel;

public class ArrangedModuleSubMenu extends ArrangedModuleMenu {
  @JsonIgnore
  private final AppModule module;

  public ArrangedModuleSubMenu(MenuModel menu, AppModule module) {
    super(menu, module);
    this.module = module;
  }

  @Override
  public String getParent() {
    return module.getFullName();
  }
}
