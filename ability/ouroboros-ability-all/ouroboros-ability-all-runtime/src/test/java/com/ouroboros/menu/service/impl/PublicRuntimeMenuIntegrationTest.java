package com.ouroboros.menu.service.impl;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import com.ouroboros.appmodule.AppModuleIntegrator;
import com.ouroboros.appmodule.runtime.RuntimeAppModuleIntegrator;
import com.ouroboros.businessmodel.ModuleExportProvider;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.model.TreeMenuModelVo;
import com.ouroboros.menu.provider.JarMenuModelProvider;
import com.ouroboros.security.rbac.provider.JarAuthorityProvider;
import com.ouroboros.uimodel.JarUIModelProvider;
import com.ouroboros.uischema.JarUISchemaProvider;
import com.ouroboros.util.Trees;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRuntimeMenuIntegrationTest {
  @TempDir
  Path appHome;
  private ModuleExportProvider previousModuleExport;

  @BeforeEach
  void initializeApplicationModules() {
    previousModuleExport = ModuleExportProvider.SingletonHolder.instance;
    new ModuleExportProvider(() -> appHome.toString())
        .onMetadataRefreshed(new OuroborosContextRefreshedEvent(this));
  }

  @AfterEach
  void restoreApplicationModules() {
    ModuleExportProvider.SingletonHolder.instance = previousModuleExport;
  }

  @ParameterizedTest
  @CsvSource({
      "systemSetting.resourceManagement, /system-setting/resource-management, 资源管理",
      "notification, /notification, 消息通知"
  })
  void grantedPublicCapabilityExposesItsMenuAndPage(String name, String path, String title) {
    var authorities = new JarAuthorityProvider().getAuthority(name);
    assertThat(authorities).hasSize(1);
    var authority = authorities.getFirst();
    assertThat(authority.getMenuItems()).contains(name);
    assertThat(authority.getUiViews()).contains(path);
    assertThat(mainMenus(authority.getMenuItems())).anySatisfy(menu -> {
      assertThat(menu.getFullName()).isEqualTo(name);
      assertThat(menu.getUrl()).isEqualTo(path);
      assertThat(menu.getTitle()).isEqualTo(title);
    });
    assertThat(new JarUIModelProvider().getUIModel(path)).isPresent();
    assertThat(new JarUISchemaProvider().getUISchema(path)).isPresent();
  }

  @ParameterizedTest
  @CsvSource({"systemSetting.resourceManagement", "notification"})
  void ungrantedPublicCapabilityDoesNotExposeItsMenu(String name) {
    assertThat(mainMenus(List.of())).noneMatch(menu -> name.equals(menu.getFullName()));
  }

  private List<? extends MenuModel> mainMenus(List<String> authorityMenus) {
    var beans = new DefaultListableBeanFactory();
    beans.registerSingleton("appModuleIntegrator", new RuntimeAppModuleIntegrator());
    var service = new MenuServiceImpl(beans.getBeanProvider(AppModuleIntegrator.class));
    var roots = service.buildMainMenus(
        List.copyOf(new JarMenuModelProvider().getAllMenus().values()), authorityMenus)
        .stream().map(TreeMenuModelVo.class::cast).toList();
    return Trees.<TreeMenuModelVo>flattenTree(roots, TreeMenuModelVo::getChildren);
  }
}
