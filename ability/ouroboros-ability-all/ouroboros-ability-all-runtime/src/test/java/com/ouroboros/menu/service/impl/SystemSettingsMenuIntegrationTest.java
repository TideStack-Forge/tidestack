package com.ouroboros.menu.service.impl;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.AppModuleIntegrator;
import com.ouroboros.appmodule.runtime.RuntimeAppModuleIntegrator;
import com.ouroboros.businessmodel.ModuleExportProvider;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.model.TreeMenuModelVo;
import com.ouroboros.menu.provider.JarMenuModelProvider;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.rbac.controller.AuthorityDefineController;
import com.ouroboros.security.rbac.controller.AuthorityDefineController.AuthorityDefineTreeNode;
import com.ouroboros.security.rbac.provider.JarAuthorityProvider;
import com.ouroboros.security.rbac.provider.SpringBeanAuthorityProvider;
import com.ouroboros.util.Json;
import com.ouroboros.util.Trees;

import static org.assertj.core.api.Assertions.assertThat;

class SystemSettingsMenuIntegrationTest {
  @TempDir
  Path appHome;
  private ModuleExportProvider previousModuleExport;
  private Object previousSpringAuthorityProvider;
  private AnnotationConfigApplicationContext authorityContext;

  @BeforeEach
  void initializeApplicationModules() {
    previousModuleExport = ModuleExportProvider.SingletonHolder.instance;
    var provider = new ModuleExportProvider(() -> appHome.toString());
    provider.onMetadataRefreshed(new OuroborosContextRefreshedEvent(this));
    previousSpringAuthorityProvider = ReflectionTestUtils.getField(
        SpringBeanAuthorityProvider.SingletonHolder.class, "AUTHORITY_PROVIDER");
    authorityContext = new AnnotationConfigApplicationContext(
        JarAuthorityProvider.class, SpringBeanAuthorityProvider.class);
  }

  @AfterEach
  void restoreApplicationModules() {
    ModuleExportProvider.SingletonHolder.instance = previousModuleExport;
    if (authorityContext != null) {
      authorityContext.close();
    }
    ReflectionTestUtils.setField(SpringBeanAuthorityProvider.SingletonHolder.class,
        "AUTHORITY_PROVIDER", previousSpringAuthorityProvider);
  }

  private static final List<String> GROUPS = List.of("organizationAndAccess", "basicConfiguration",
      "portalAndInterface", "workflowManagement", "auditManagement", "operationsManagement");
  private static final Map<String, List<String>> PAGES = Map.of(
      "organizationAndAccess", List.of("organizationManagement", "userManagement", "roleManagement", "adminLevelManagement"),
      "basicConfiguration", List.of("configurationManagement", "dataDictManagement", "i18nManagement", "integrationManagement"),
      "portalAndInterface", List.of("WidgetManage", "UserDashboardTemplateManage"),
      "workflowManagement", List.of("engineAdapterCatalog", "engineMappingManage", "externalResultTracking"),
      "auditManagement", List.of("businessLog", "businessLogTemplate"),
      "operationsManagement", List.of("rpcRuntimeManagement", "resourceManagement", "platformCache"));

  @Test
  void publicRuntimeOrganizesAvailableSettingsIntoSixOrderedGroups() {
    var menus = new JarMenuModelProvider().getAllMenus();
    var root = settings(mainMenus(PAGES.values().stream().flatMap(List::stream).map(SystemSettingsMenuIntegrationTest::fullName).toList()));
    assertThat(root.getTitle()).isEqualTo("系统设置");
    assertThat(root.getChildren()).extracting(MenuModel::getFullName)
        .containsExactlyElementsOf(GROUPS.stream().map(SystemSettingsMenuIntegrationTest::fullName).toList());
    assertThat(root.getChildren()).extracting(MenuModel::getTitle)
        .containsExactly("组织与权限", "基础配置", "门户与界面", "流程管理", "审计管理", "运行维护");
    for (int index = 0; index < GROUPS.size(); index++) {
      var group = root.getChildren().get(index);
      assertThat(group.getUrl()).isNull();
      assertThat(group.getChildren()).extracting(MenuModel::getFullName)
          .containsExactlyElementsOf(PAGES.get(GROUPS.get(index)).stream().map(SystemSettingsMenuIntegrationTest::fullName).toList());
      for (var page : group.getChildren()) {
        assertThat(page.getChildren()).isEmpty();
        assertThat(page.getUrl()).isNotBlank().isEqualTo(menus.get(page.getFullName()).getUrl());
      }
    }
    System.out.println("SYSTEM_SETTINGS_MENU_TREE=" + Json.toJsonString(root));
  }

  @ParameterizedTest
  @MethodSource("pagePlacements")
  void existingLeafGrantShowsOnlyItsNewNavigationPath(String groupName, String pageName) {
    var root = settings(mainMenus(List.of(fullName(pageName))));
    assertThat(root.getChildren()).singleElement().satisfies(group -> {
      assertThat(group.getFullName()).isEqualTo(fullName(groupName));
      assertThat(group.getChildren()).extracting(MenuModel::getFullName).containsExactly(fullName(pageName));
    });
  }

  @Test
  void existingRoleAuthorityRemainsUsableWithoutGrantingNewGroupPermissions() {
    var authorities = new JarAuthorityProvider().getAuthority(fullName("roleManagement"));
    assertThat(authorities).hasSize(1);
    var authority = authorities.getFirst();
    var root = settings(mainMenus(authority.getMenuItems()));
    assertThat(root.getChildren()).singleElement().satisfies(group -> {
      assertThat(group.getTitle()).isEqualTo("组织与权限");
      assertThat(group.getChildren()).extracting(MenuModel::getFullName).containsExactly(fullName("roleManagement"));
    });
  }

  @Test
  void noPermissionsLeaveNoEmptySettingsGroups() {
    assertThat(mainMenus(List.of())).isEmpty();
  }

  @Test
  void runtimeModuleTreeUsesTheSameGroupsAndPageParentsAsMenus() {
    var modules = new RuntimeAppModuleIntegrator().getAppModules();
    assertThat(modules.stream().filter(module -> "systemSetting".equals(module.getParent())))
        .extracting(AppModule::getFullName)
        .containsExactlyElementsOf(GROUPS.stream().map(SystemSettingsMenuIntegrationTest::fullName).toList());
    var byName = modules.stream().collect(Collectors.toMap(AppModule::getFullName, Function.identity()));
    PAGES.forEach((group, pages) -> pages.forEach(page ->
        assertThat(byName.get(fullName(page)).getParent()).isEqualTo(fullName(group))));
    System.out.println("SYSTEM_SETTINGS_MODULES=" + Json.toJsonString(modules.stream()
        .filter(module -> module.getFullName().startsWith("systemSetting")).toList()));
  }

  @Test
  void rolePermissionTreeKeepsEveryReferencedSettingsAuthorityReachable() {
    var integrator = new RuntimeAppModuleIntegrator();
    var beans = new DefaultListableBeanFactory();
    beans.registerSingleton("appModuleIntegrator", integrator);
    var tree = new AuthorityDefineController(beans.getBeanProvider(AppModuleIntegrator.class)).tree()
        .getData().getItems();
    var nodes = Trees.flattenTree(List.copyOf(tree), AuthorityDefineTreeNode::getChildren);
    var byName = nodes.stream().collect(Collectors.toMap(AuthorityDefineTreeNode::getFullName, Function.identity()));
    System.out.println("SYSTEM_SETTINGS_AUTHORITY_TREE=" + Json.toJsonString(tree));
    System.out.println("SYSTEM_SETTINGS_AUTHORITY_PARENTS=" + Json.toJsonString(integrator
        .rearrangeAuthorities(AuthorityCenter.getAllAuthority()).stream()
        .filter(authority -> authority.getFullName().startsWith("systemSetting."))
        .map(authority -> Map.of("fullName", authority.getFullName(), "parent", authority.getParent())).toList()));
    assertThat(byName.get("systemSetting").getChildren()).extracting(AuthorityDefineTreeNode::getFullName)
        .containsExactlyElementsOf(GROUPS.stream().map(SystemSettingsMenuIntegrationTest::fullName).toList());
    var expected = integrator.getAppModules().stream()
        .filter(module -> module.getFullName().startsWith("systemSetting."))
        .map(AppModule::getAuthorities).filter(Objects::nonNull).flatMap(List::stream).distinct().toList();
    assertThat(byName.keySet()).containsAll(expected);
    assertThat(byName.keySet()).containsAll(AuthorityCenter.getAllAuthority().keySet().stream()
        .filter(name -> name.startsWith("systemSetting.")).toList());
    PAGES.forEach((group, pages) -> pages.forEach(page ->
        assertThat(byName.get(fullName(page)).getParent()).isEqualTo(fullName(group))));
    assertThat(byName.get("systemSetting.integrationManagement.applications.authServer.settings").getParent())
        .isEqualTo("systemSetting.integrationManagement.applications");
    assertThat(byName.get("systemSetting.userManagement.profile").getParent())
        .isEqualTo("systemSetting.userManagement");
    assertThat(byName.get("systemSetting.userProfile.configure").getParent())
        .isEqualTo("systemSetting.configurationManagement");
    assertThat(byName.get("systemSetting.notificationTemplate").getParent())
        .isEqualTo("systemSetting.basicConfiguration");
  }

  @Test
  void authorityRearrangementPreservesOriginalApiPageMenuAndAccessControlGrants() {
    var raw = AuthorityCenter.getAllAuthority();
    assertThat(raw).containsKeys("systemSetting.roleManagement", "systemSetting.integrationManagement.applications.manage");
    var arranged = new RuntimeAppModuleIntegrator().rearrangeAuthorities(raw);
    for (var authority : arranged) {
      var original = raw.get(authority.getFullName());
      if (original != null && authority.getFullName().startsWith("systemSetting.")) {
        assertThat(authority.getApis()).isEqualTo(original.getApis());
        assertThat(authority.getUiViews()).isEqualTo(original.getUiViews());
        assertThat(authority.getMenuItems()).isEqualTo(original.getMenuItems());
        assertThat(authority.getAccessControlDefine()).isSameAs(original.getAccessControlDefine());
      }
    }
  }

  private static Stream<Arguments> pagePlacements() {
    return GROUPS.stream().flatMap(group -> PAGES.get(group).stream().map(page -> Arguments.of(group, page)));
  }

  private static String fullName(String name) {
    return "systemSetting." + name;
  }

  private static TreeMenuModelVo settings(List<? extends MenuModel> roots) {
    return roots.stream().filter(menu -> "systemSetting".equals(menu.getFullName()))
        .map(TreeMenuModelVo.class::cast).findFirst().orElseThrow();
  }

  private static List<? extends MenuModel> mainMenus(List<String> grants) {
    var beans = new DefaultListableBeanFactory();
    beans.registerSingleton("appModuleIntegrator", new RuntimeAppModuleIntegrator());
    var service = new MenuServiceImpl(beans.getBeanProvider(AppModuleIntegrator.class));
    return service.buildMainMenus(List.copyOf(new JarMenuModelProvider().getAllMenus().values()), grants);
  }
}
