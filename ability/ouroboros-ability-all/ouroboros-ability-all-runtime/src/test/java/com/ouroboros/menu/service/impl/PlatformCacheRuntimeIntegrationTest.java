package com.ouroboros.menu.service.impl;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import com.ouroboros.appmodule.AppModuleIntegrator;
import com.ouroboros.appmodule.JarAppModuleProvider;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.model.TreeMenuModelVo;
import com.ouroboros.menu.provider.JarMenuModelProvider;
import com.ouroboros.security.rbac.provider.JarAuthorityProvider;
import com.ouroboros.uimodel.JarUIModelProvider;
import com.ouroboros.uischema.JarUISchemaProvider;
import com.ouroboros.util.Trees;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformCacheRuntimeIntegrationTest {
  private static final String CACHE_MENU = "systemSetting.platformCache";

  @Test
  void authorizedApplicationCanSeeCacheMenuWithoutDevelopmentModules() {
    assertThat(mainMenus(List.of(CACHE_MENU))).anySatisfy(menu -> {
      assertThat(menu.getFullName()).isEqualTo(CACHE_MENU);
      assertThat(menu.getUrl()).isEqualTo("/system-setting/platform-cache");
      assertThat(menu.getTitle()).isEqualTo("平台缓存");
    });
  }

  @Test
  void applicationWithoutCacheAuthorityCannotSeeCacheMenu() {
    assertThat(mainMenus(List.of())).noneMatch(menu -> CACHE_MENU.equals(menu.getFullName()));
  }

  @Test
  void ordinaryRuntimeLoadsCacheAuthorityAndPageThroughMetadataProviders() {
    assertThat(new JarAuthorityProvider().getAuthority(CACHE_MENU)).singleElement().satisfies(authority -> {
      assertThat(authority.getMenuItems()).containsExactly(CACHE_MENU);
      assertThat(authority.getUiViews()).containsExactly("/system-setting/platform-cache");
      assertThat(authority.getApis()).contains("GET /api/platform-cache/summary", "DELETE /api/platform-cache");
    });
    assertThat(new JarUIModelProvider().getUIModel("/system-setting/platform-cache")).isPresent();
    assertThat(new JarUISchemaProvider().getUISchema("/system-setting/platform-cache")).isPresent();
  }

  private List<? extends MenuModel> mainMenus(List<String> authorityMenus) {
    var beans = new DefaultListableBeanFactory();
    AppModuleIntegrator integrator = new JarAppModuleProvider()::getAppModules;
    beans.registerSingleton("appModuleIntegrator", integrator);
    var service = new MenuServiceImpl(beans.getBeanProvider(AppModuleIntegrator.class));
    var roots = service.buildMainMenus(
        List.copyOf(new JarMenuModelProvider().getAllMenus().values()), authorityMenus)
        .stream().map(TreeMenuModelVo.class::cast).toList();
    return Trees.<TreeMenuModelVo>flattenTree(roots, TreeMenuModelVo::getChildren);
  }
}
