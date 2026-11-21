package com.ouroboros.menu.service.impl;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.AppModuleIntegrator;
import com.ouroboros.appmodule.BaseAppModule;
import com.ouroboros.menu.BaseMenuModel;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.model.TreeMenuModelVo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuServiceImplModuleHierarchyTest {
  private final List<AppModule> modules = List.of(
      module("settings", null, 1),
      module("settings.access", "settings", 1),
      module("settings.basic", "settings", 2),
      module("settings.roles", "settings.access", 2),
      module("settings.users", "settings.access", 1),
      module("settings.config", "settings.basic", 1));
  private final List<MenuModel> menus = List.of(
      menu("settings.roles", "/roles"), menu("settings.users", "/users"),
      menu("settings.config", "/config"));

  @Test
  void leafGrantRetainsItsActualModuleAncestorsWithoutSiblings() {
    var roots = service(modules).buildMainMenus(menus, List.of("SETTINGS.ROLES"));

    var root = only(roots, "settings");
    var group = only(root.getChildren(), "settings.access");
    var leaf = only(group.getChildren(), "settings.roles");
    assertEquals("/roles", leaf.getUrl());
    assertNull(group.getUrl());
  }

  @Test
  void emptyGrantsOrDirectoryOnlyGrantDoNotExposePagesOrEmptyGroups() {
    var service = service(modules);
    assertTrue(service.buildMainMenus(menus, List.of()).isEmpty());
    assertTrue(service.buildMainMenus(menus, List.of("settings.access")).isEmpty());
  }

  @Test
  void ancestorRetainedForNavigationDoesNotAcquireItsOwnPagePermission() {
    var roots = service(modules).buildMainMenus(
        List.of(menu("settings.access", "/access"), menu("settings.roles", "/roles")),
        List.of("settings.roles"));

    var group = only(only(roots, "settings").getChildren(), "settings.access");
    assertNull(group.getUrl());
    assertEquals("/roles", only(group.getChildren(), "settings.roles").getUrl());
  }

  @Test
  void explicitlyGrantedAncestorKeepsItsPageAndModuleOrdering() {
    var roots = service(modules).buildMainMenus(
        List.of(menu("settings.access", "/access"), menus.get(0), menus.get(1), menus.get(2)),
        List.of("settings.access", "settings.roles", "settings.users", "settings.config"));

    var groups = only(roots, "settings").getChildren();
    assertEquals(List.of("settings.access", "settings.basic"), names(groups));
    assertEquals("/access", groups.get(0).getUrl());
    assertEquals(List.of("settings.users", "settings.roles"), names(groups.get(0).getChildren()));
  }

  @Test
  void legacyDottedHierarchyStillRetainsAncestors() {
    var roots = service(List.of(module("legacy", null, 0), module("legacy.page", "legacy", 0)))
        .buildMainMenus(List.of(menu("legacy.page", "/legacy")), List.of("legacy.page"));
    assertEquals("/legacy", only(only(roots, "legacy").getChildren(), "legacy.page").getUrl());
  }

  @Test
  void structuralAncestorsDoNotCrossMainAndSecondaryScopes() {
    var secondaryParent = menu("settings.access", "/secondary");
    secondaryParent.setPlacement(MenuModel.MenuScope.SECONDARY);
    var service = service(modules);
    assertTrue(service.buildMainMenus(List.of(secondaryParent, menus.get(0)), List.of("settings.roles")).isEmpty());
    assertTrue(service.buildSecondaryMenus(List.of(), List.of(secondaryParent, menus.get(0)),
        List.of("settings.roles")).isEmpty());
  }

  @Test
  void cyclicOrMissingParentsCannotHangOrExposeDisconnectedMenus() {
    var service = service(List.of(
        module("cycle.a", "cycle.b", 0), module("cycle.b", "cycle.a", 0),
        module("orphan", "missing", 0)));
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertTrue(service.buildMainMenus(
        List.of(menu("cycle.a", "/a"), menu("orphan", "/orphan")),
        List.of("cycle.a", "orphan")).isEmpty()));
  }

  private static MenuServiceImpl service(List<AppModule> modules) {
    var beans = new DefaultListableBeanFactory();
    beans.registerSingleton("appModuleIntegrator", (AppModuleIntegrator) () -> modules);
    return new MenuServiceImpl(beans.getBeanProvider(AppModuleIntegrator.class));
  }

  private static BaseAppModule module(String name, String parent, int order) {
    var module = new BaseAppModule();
    module.setFullName(name);
    module.setParent(parent);
    module.setTitle(name);
    module.setMenu(name);
    module.setOrder(order);
    return module;
  }

  private static BaseMenuModel menu(String name, String url) {
    var menu = new BaseMenuModel();
    menu.setFullName(name);
    menu.setTitle(name);
    menu.setUrl(url);
    return menu;
  }

  private static TreeMenuModelVo only(List<? extends MenuModel> menus, String name) {
    assertEquals(List.of(name), names(menus));
    return (TreeMenuModelVo) menus.get(0);
  }

  private static List<String> names(List<? extends MenuModel> menus) {
    return menus.stream().map(MenuModel::getFullName).toList();
  }
}
