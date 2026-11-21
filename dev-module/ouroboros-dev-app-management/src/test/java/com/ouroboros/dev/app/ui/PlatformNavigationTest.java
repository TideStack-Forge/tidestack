package com.ouroboros.dev.app.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.AppModuleIntegrator;
import com.ouroboros.menu.BaseMenuModel;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.metadata.resource.ClasspathMetadataSource;
import com.ouroboros.metadata.resource.TypedMetadataCatalog;
import com.ouroboros.metadata.resource.TypedMetadataSpec;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.rbac.provider.JarAuthorityProvider;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformNavigationTest {

  @Test
  void runtimeInstancesAreImmediatelyBelowOpenAppsWithoutAnApplicationContext() throws IOException {
    var spec = TypedMetadataSpec.<BaseMenuModel>builder(BaseMenuModel.class)
        .typedSuffix(".menu.json")
        .jsonParser(BaseMenuModel.class)
        .keyExtractor(BaseMenuModel::getFullName)
        .build();
    var menus = new TypedMetadataCatalog(new ClasspathMetadataSource()).load(spec);

    var mainMenus = platformModules().rearrangeMenus(new ArrayList<>(menus.values().values())).stream()
        .filter(menu -> menu.getScope() == MenuModel.MenuScope.MAIN)
        .toList();

    assertEquals(List.of("Apps", "ContainerManagement", "AppTemplates"),
        mainMenus.stream().map(MenuModel::getFullName).toList());
    assertEquals("运行实例", mainMenus.get(1).getTitle());
    assertEquals("/container-management", mainMenus.get(1).getUrl());
  }

  @Test
  void runtimeNavigationIsIncludedInThePlatformAuthorityTree() throws IOException {
    var authorities = new JarAuthorityProvider().getAllAuthorities().stream()
        .collect(Collectors.toMap(Authority::getFullName, Function.identity()));

    var runtimeAuthority = platformModules().rearrangeAuthorities(authorities).stream()
        .filter(authority -> "ContainerManagement".equals(authority.getFullName()))
        .findFirst()
        .orElseThrow();

    assertTrue(runtimeAuthority.getMenuItems().contains("ContainerManagement"));
    assertTrue(runtimeAuthority.getUiViews().contains("/container-management"));
    assertTrue(runtimeAuthority.getApis().contains("GET /api/app/container"));
  }

  private AppModuleIntegrator platformModules() throws IOException {
    try (var input = getClass().getResourceAsStream("/META-INF/ouroboros/metadata/app-modules.json")) {
      assertNotNull(input);
      List<AppModule> modules = Json.toList(new TypeReference<List<AppModule>>() {
      }, new String(input.readAllBytes(), StandardCharsets.UTF_8));
      return () -> modules;
    }
  }
}
