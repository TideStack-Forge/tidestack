package com.ouroboros.dev.app.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.dev.app.model.DevMenuModel;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevMenuServiceImplTest {

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("tester", "n/a"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void mainMenusAreUniqueAndSortedByProductOrder() {
    DevMenuServiceImpl service = new DevMenuServiceImpl();

    List<DevMenuModel> mainMenus = service.getMainMenus("demo");
    List<String> rootFullNames = mainMenus.stream().map(DevMenuModel::getFullName).collect(Collectors.toList());

    assertUniqueFullNames(flatten(mainMenus));
    assertEquals("应用开发看板", mainMenus.get(0).getTitle());
    assertTrue(rootFullNames.indexOf("OrganizationManagement") > -1);
    assertTrue(rootFullNames.indexOf("ApplicationManagement") > -1);
    assertTrue(rootFullNames.indexOf("OrganizationManagement") < rootFullNames.indexOf("ApplicationManagement"));
  }

  @Test
  void duplicateFullNamesAreMergedBeforeBuildingTheTree() {
    DevMenuServiceImpl service = new DevMenuServiceImpl(java.util.Arrays.asList(
        menu("AppDevDashboard", "应用开发看板", 100, DevMenuModel.MenuScope.MAIN),
        menu("AppDevDashboard", "应用开发看板-重复", 0, DevMenuModel.MenuScope.MAIN),
        menu("OrganizationManagement", "组织架构", 10, DevMenuModel.MenuScope.MAIN),
        menu("OrganizationManagement.BuiltinRoleManagement", "内置角色管理", 10, DevMenuModel.MenuScope.MAIN)));

    List<DevMenuModel> mainMenus = service.getMainMenus("demo");

    assertEquals(2, mainMenus.size());
    assertEquals("应用开发看板-重复", mainMenus.get(0).getTitle());
    assertEquals(1, findMenu(mainMenus, "OrganizationManagement")
        .map(DevMenuModel::getChildren)
        .map(List::size)
        .orElse(0));
  }

  @Test
  void builtinRoleAndPositionMenusAreGroupedUnderOrganizationManagement() {
    DevMenuServiceImpl service = new DevMenuServiceImpl();

    DevMenuModel organizationManagement = findMenu(service.getMainMenus("demo"), "OrganizationManagement")
        .orElseThrow(AssertionError::new);

    assertEquals("组织架构", organizationManagement.getTitle());
    assertNotNull(organizationManagement.getChildren());
    assertEquals("内置角色管理", organizationManagement.getChildren().get(0).getTitle());
    assertEquals("内置岗位管理", organizationManagement.getChildren().get(1).getTitle());
  }

  @Test
  void uiModelAndSchemaAreOrderedUnderTheFrontendPageMenu() {
    DevMenuServiceImpl service = new DevMenuServiceImpl();

    DevMenuModel uiDesign = findMenu(service.getMainMenus("demo"), "UIDesign")
        .orElseThrow(AssertionError::new);
    DevMenuModel frontendPages = findMenu(uiDesign.getChildren(), "UIDesign.UIManagement")
        .orElseThrow(AssertionError::new);

    assertEquals("前端页面", frontendPages.getTitle());
    assertEquals("/dev/demo/ui", frontendPages.getUrl());
    assertNotNull(frontendPages.getChildren());
    assertEquals(List.of("UIDesign.UIManagement.UIModel", "UIDesign.UIManagement.UISchema"),
        frontendPages.getChildren().stream().map(DevMenuModel::getFullName).toList());
    assertEquals(List.of("UI 模型", "UI Schema"),
        frontendPages.getChildren().stream().map(DevMenuModel::getTitle).toList());
    assertEquals(List.of("/dev/demo/ui-model", "/dev/demo/ui-schema"),
        frontendPages.getChildren().stream().map(DevMenuModel::getUrl).toList());
    assertTrue(uiDesign.getChildren().stream().noneMatch(menu ->
        "UIDesign.UIModel".equals(menu.getFullName()) || "UIDesign.UISchema".equals(menu.getFullName())));
  }

  @Test
  void logicflowDebugSessionsAreAvailableUnderBusinessLogic() {
    DevMenuServiceImpl service = new DevMenuServiceImpl();

    DevMenuModel businessLogic = findMenu(service.getMainMenus("demo"), "BusinessLogic")
        .orElseThrow(AssertionError::new);
    DevMenuModel debugSessions = findMenu(businessLogic.getChildren(), "BusinessLogic.LogicflowDebugSessions")
        .orElseThrow(AssertionError::new);

    assertEquals("逻辑流调试会话", debugSessions.getTitle());
    assertEquals("/dev/demo/logicflow/debug/sessions", debugSessions.getUrl());
  }

  @Test
  void secondaryMenusAreUnique() {
    DevMenuServiceImpl service = new DevMenuServiceImpl();

    assertUniqueFullNames(service.getSecondaryMenus("demo"));
  }

  @Test
  void runtimeInstancesRemainAvailableInsideAnApplication() {
    DevMenuServiceImpl service = new DevMenuServiceImpl();

    DevMenuModel runtimeMenu = findMenu(service.getSecondaryMenus("demo"), "ContainerManagement")
        .orElseThrow(AssertionError::new);

    assertEquals("运行实例", runtimeMenu.getTitle());
    assertEquals("/container-management", runtimeMenu.getUrl());
  }

  @Test
  void extraPropsArePreservedWhenMenuJsonDefinesFixedTabFlags() {
    DevMenuServiceImpl service = new DevMenuServiceImpl(java.util.Arrays.asList(
        menu("ApplicationManagement", "应用管理", 100, DevMenuModel.MenuScope.MAIN, "\"isFixedTab\":true"),
        menu("ApplicationManagement.Child", "子菜单", 110, DevMenuModel.MenuScope.MAIN)));

    DevMenuModel applicationManagement = findMenu(service.getMainMenus("demo"), "ApplicationManagement")
        .orElseThrow(AssertionError::new);

    assertEquals(Boolean.TRUE, applicationManagement.getExtraProp("isFixedTab"));
  }

  @Test
  void devMenuModelParsesPlacementAndLegacyScopeAlias() {
    DevMenuModel placementMenu = Json.toBean(DevMenuModel.class,
        "{\"fullName\":\"DevDocs\",\"title\":\"开发文档\",\"url\":\"/dev-docs\",\"placement\":\"secondary\",\"stage\":\"dev\",\"order\":1}");
    DevMenuModel legacyScopeMenu = Json.toBean(DevMenuModel.class,
        "{\"fullName\":\"LegacyDevDocs\",\"title\":\"旧开发文档\",\"url\":\"/dev-docs\",\"scope\":\"secondary\",\"stage\":\"dev\",\"order\":1}");

    assertEquals(DevMenuModel.MenuScope.SECONDARY, placementMenu.getScope());
    assertEquals(DevMenuModel.MenuScope.SECONDARY, legacyScopeMenu.getScope());
    assertEquals(null, placementMenu.getExtraProp("placement"));
    assertEquals(null, legacyScopeMenu.getExtraProp("scope"));
  }

  private static void assertUniqueFullNames(List<DevMenuModel> menus) {
    Set<String> fullNames = new HashSet<>();
    for (DevMenuModel menu : menus) {
      assertTrue(fullNames.add(menu.getFullName()), "duplicate menu fullName: " + menu.getFullName());
    }
  }

  private static List<DevMenuModel> flatten(List<DevMenuModel> menus) {
    List<DevMenuModel> flattened = new ArrayList<>();
    for (DevMenuModel menu : menus) {
      flattened.add(menu);
      if (menu.getChildren() != null) {
        flattened.addAll(flatten(menu.getChildren()));
      }
    }
    return flattened;
  }

  private static Optional<DevMenuModel> findMenu(List<DevMenuModel> menus, String fullName) {
    return flatten(menus).stream()
        .filter(menu -> fullName.equals(menu.getFullName()))
        .findFirst();
  }

  private static DevMenuModel menu(String fullName, String title, int order, DevMenuModel.MenuScope scope) {
    return menu(fullName, title, order, scope, null);
  }

  private static DevMenuModel menu(String fullName, String title, int order, DevMenuModel.MenuScope scope,
      String extraJson) {
    String json = String.format(
        "{\"fullName\":\"%s\",\"title\":\"%s\",\"url\":\"%s\",\"placement\":\"%s\",\"stage\":\"dev\",\"order\":%d%s}",
        fullName,
        title,
        "/dev/demo/" + fullName,
        scope.getValue(),
        order,
        extraJson == null || extraJson.isEmpty() ? "" : "," + extraJson);
    return Json.toBean(DevMenuModel.class, json);
  }
}
