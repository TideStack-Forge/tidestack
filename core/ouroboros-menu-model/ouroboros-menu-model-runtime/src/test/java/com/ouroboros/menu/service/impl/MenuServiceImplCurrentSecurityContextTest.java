package com.ouroboros.menu.service.impl;

import java.util.Collections;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.AppModuleIntegrator;
import com.ouroboros.menu.BaseMenuModel;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelShowModeEnum;
import com.ouroboros.menu.model.TreeMenuModelVo;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MenuServiceImplCurrentSecurityContextTest {

  @AfterEach
  void clearSecurityContextHolder() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void getMainMenusUsesCurrentSecurityContextIdentityAuthorities() {
    CurrentSecurityContextHolder.setDelegate(new IdentityOnlySecurityContext(new DefaultSecurityIdentity(new TestUser(
        "seam-user",
        Collections.singletonList(new TestRole(
            "role-admin",
            "Admin",
            Collections.singletonList(new TestGrantedAuthority(new TestAuthority(
                "fixture.roleManagement",
                Collections.singletonList("systemSetting.roleManagement"))))))))));

    MenuServiceImpl service = new MenuServiceImpl(new SingleAppModuleIntegratorProvider(() -> Collections.singletonList(
        new TestAppModule("roleManagement", "角色管理", "systemSetting.roleManagement"))));

    List<? extends MenuModel> mainMenus = service.getMainMenus("ignored-user");

    assertEquals(1, mainMenus.size());
    TreeMenuModelVo menu = assertInstanceOf(TreeMenuModelVo.class, mainMenus.get(0));
    assertEquals("systemSetting.roleManagement", menu.getFullName());
    assertEquals("/system-setting/role-management", menu.getUrl());
    assertEquals("角色管理", menu.getTitle());
    assertEquals(0, menu.getChildren().size());
  }

  @Test
  void delegatedIdentityCannotDisplayMenusOutsideItsCredentialBoundary() {
    var user = new TestUser("delegated-user", List.of(new TestRole("role-admin", "Admin",
        List.of(new TestGrantedAuthority(new TestAuthority("fixture.roleManagement", List.of("systemSetting.roleManagement")))))));
    var identity = new DefaultSecurityIdentity(user,
        com.ouroboros.security.identity.AuthorizationBoundary.restricted("platform-api", java.util.Map.of()),
        new com.ouroboros.security.identity.CredentialContext(
            com.ouroboros.security.identity.CredentialContext.Kind.DELEGATED, "client", "grant", "platform-api"));
    CurrentSecurityContextHolder.setDelegate(new IdentityOnlySecurityContext(identity));
    MenuServiceImpl service = new MenuServiceImpl(new SingleAppModuleIntegratorProvider(() -> List.of(
        new TestAppModule("roleManagement", "角色管理", "systemSetting.roleManagement"))));
    assertEquals(0, service.getMainMenus("ignored-user").size());
  }

  @Test
  void scopedSecondaryMenuModelsAreExcludedFromMainMenus() {
    MenuServiceImpl service = new MenuServiceImpl(new SingleAppModuleIntegratorProvider(() -> Arrays.asList(
        new TestAppModule("mainDocs", "主菜单文档", "MainDocs"),
        new TestAppModule("secondaryDocs", "二级菜单文档", "SecondaryDocs"))));

    List<? extends MenuModel> mainMenus = service.buildMainMenus(
        Arrays.asList(
            new TestMenuModel("MainDocs", "/main-docs", "主菜单文档", MenuModel.MenuScope.MAIN),
            new TestMenuModel("SecondaryDocs", "/secondary-docs", "二级菜单文档", MenuModel.MenuScope.SECONDARY)),
        Arrays.asList("MainDocs", "SecondaryDocs"));

    assertEquals(1, mainMenus.size());
    assertEquals("MainDocs", mainMenus.get(0).getFullName());
  }

  @Test
  void secondaryMenusMergeLegacyDirectoryAndScopedMenuModels() {
    MenuServiceImpl service = new MenuServiceImpl(new SingleAppModuleIntegratorProvider(() -> Collections.singletonList(
        new TestAppModule("secondaryDocs", "二级菜单文档", "SecondaryDocs"))));

    List<MenuModel> secondaryMenus = service.buildSecondaryMenus(
        Collections.singletonList(new TestMenuModel("LegacyDocs", "/legacy-docs", "旧二级菜单", MenuModel.MenuScope.MAIN)),
        Collections.singletonList(new TestMenuModel("SecondaryDocs", "/secondary-docs", "二级菜单文档", MenuModel.MenuScope.SECONDARY)),
        Collections.singletonList("SecondaryDocs"));

    assertEquals(2, secondaryMenus.size());
    assertEquals("LegacyDocs", secondaryMenus.get(0).getFullName());
    assertEquals("SecondaryDocs", secondaryMenus.get(1).getFullName());
  }

  @Test
  void legacySecondaryMenusWinWhenBothSourcesUseTheSameMenuName() {
    MenuServiceImpl service = new MenuServiceImpl(new SingleAppModuleIntegratorProvider(() -> Collections.singletonList(
        new TestAppModule("secondaryDocs", "二级菜单文档", "SecondaryDocs"))));

    List<MenuModel> secondaryMenus = service.buildSecondaryMenus(
        Collections.singletonList(new TestMenuModel("SecondaryDocs", "/legacy-secondary-docs", "旧二级菜单", MenuModel.MenuScope.MAIN)),
        Collections.singletonList(new TestMenuModel("SecondaryDocs", "/scoped-secondary-docs", "新二级菜单", MenuModel.MenuScope.SECONDARY)),
        Collections.singletonList("SecondaryDocs"));

    assertEquals(1, secondaryMenus.size());
    assertEquals("/legacy-secondary-docs", secondaryMenus.get(0).getUrl());
  }

  @Test
  void baseMenuModelParsesPlacementAndLegacyScopeAlias() {
    BaseMenuModel placementMenu = Json.toBean(BaseMenuModel.class,
        "{\"fullName\":\"SecondaryDocs\",\"title\":\"二级菜单文档\",\"url\":\"/secondary-docs\",\"placement\":\"secondary\"}");
    BaseMenuModel legacyScopeMenu = Json.toBean(BaseMenuModel.class,
        "{\"fullName\":\"LegacyDocs\",\"title\":\"旧菜单文档\",\"url\":\"/legacy-docs\",\"scope\":\"secondary\"}");

    assertEquals(MenuModel.MenuScope.SECONDARY, placementMenu.getScope());
    assertEquals(MenuModel.MenuScope.SECONDARY, legacyScopeMenu.getScope());
    assertEquals(null, placementMenu.getExtraProp("placement"));
    assertEquals(null, legacyScopeMenu.getExtraProp("scope"));
  }

  private static final class IdentityOnlySecurityContext implements CurrentSecurityContext {

    private final SecurityIdentity identity;

    private IdentityOnlySecurityContext(SecurityIdentity identity) {
      this.identity = identity;
    }

    @Override
    public Optional<SecurityIdentity> findIdentity() {
      return Optional.of(identity);
    }
  }

  private static final class SingleAppModuleIntegratorProvider implements ObjectProvider<AppModuleIntegrator> {

    private final AppModuleIntegrator integrator;

    private SingleAppModuleIntegratorProvider(AppModuleIntegrator integrator) {
      this.integrator = integrator;
    }

    @Override
    public AppModuleIntegrator getObject() {
      return integrator;
    }

    @Override
    public AppModuleIntegrator getObject(Object... args) {
      return integrator;
    }

    @Override
    public AppModuleIntegrator getIfAvailable() {
      return integrator;
    }

    @Override
    public AppModuleIntegrator getIfUnique() {
      return integrator;
    }

    @Override
    public Iterator<AppModuleIntegrator> iterator() {
      return Collections.singletonList(integrator).iterator();
    }

    @Override
    public Stream<AppModuleIntegrator> stream() {
      return Stream.of(integrator);
    }

    @Override
    public Stream<AppModuleIntegrator> orderedStream() {
      return stream();
    }
  }

  private static final class TestAppModule implements AppModule {

    private final String fullName;
    private final String title;
    private final String menu;

    private TestAppModule(String fullName, String title, String menu) {
      this.fullName = fullName;
      this.title = title;
      this.menu = menu;
    }

    @Override
    public String getFullName() {
      return fullName;
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public String getDescription() {
      return null;
    }

    @Override
    public String getIcon() {
      return null;
    }

    @Override
    public Integer getOrder() {
      return 0;
    }

    @Override
    public String getMenu() {
      return menu;
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

  private static final class TestMenuModel implements MenuModel {

    private final String fullName;
    private final String url;
    private final String title;
    private final MenuScope scope;

    private TestMenuModel(String fullName, String url, String title, MenuScope scope) {
      this.fullName = fullName;
      this.url = url;
      this.title = title;
      this.scope = scope;
    }

    @Override
    public String getUrl() {
      return url;
    }

    @Override
    public String getFullName() {
      return fullName;
    }

    @Override
    public String getIcon() {
      return null;
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public MenuModelShowModeEnum getShowMode() {
      return MenuModelShowModeEnum.TAB;
    }

    @Override
    public Integer getOrder() {
      return 0;
    }

    @Override
    public MenuScope getScope() {
      return scope;
    }
  }

  private static final class TestUser implements User {

    private final String userIdentity;
    private final List<Role> roles;

    private TestUser(String userIdentity, List<Role> roles) {
      this.userIdentity = userIdentity;
      this.roles = roles;
    }

    @Override
    public String getUserIdentity() {
      return userIdentity;
    }

    @Override
    public List<Role> getRoles() {
      return roles;
    }
  }

  private static final class TestRole implements Role {

    private final String id;
    private final String name;
    private final List<? extends OuroborosGrantedAuthority> authorities;

    private TestRole(String id, String name, List<? extends OuroborosGrantedAuthority> authorities) {
      this.id = id;
      this.name = name;
      this.authorities = authorities;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public List<? extends OuroborosGrantedAuthority> getAuthorities() {
      return authorities;
    }
  }

  private static final class TestGrantedAuthority implements OuroborosGrantedAuthority {

    private final Authority authority;

    private TestGrantedAuthority(Authority authority) {
      this.authority = authority;
    }

    @Override
    public String getAuthority() {
      return authority.getFullName();
    }

    @Override
    public Authority getAuthorityObject() {
      return authority;
    }

    @Override
    public AccessControlParams getAccessControlParams() {
      return AccessControlParams.empty();
    }
  }

  private static final class TestAuthority implements Authority {

    private final String fullName;
    private final List<String> menuItems;

    private TestAuthority(String fullName, List<String> menuItems) {
      this.fullName = fullName;
      this.menuItems = menuItems;
    }

    @Override
    public String getFullName() {
      return fullName;
    }

    @Override
    public String getTitle() {
      return fullName;
    }

    @Override
    public AccessControlDefine getAccessControlDefine() {
      return null;
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
      return menuItems;
    }
  }
}
