package com.ouroboros.appmodule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.appmodule.model.ArrangedModuleAuthority;
import com.ouroboros.appmodule.model.PlacementModuleAuthority;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.DefaultAuthority;

class AppModuleIntegratorAuthorityTest {

  @Test
  void shouldPreferOwnModuleProjectionForRepeatedAuthority() {
    var role = authority("systemSetting.roleManagement", "角色权限");
    var integrator = integrator(
        module("systemSetting.organizationAndAccess", "组织与权限", 98, List.of(role.getFullName())),
        module("systemSetting.roleManagement", "角色", 210, List.of(role.getFullName())));

    var result = integrator.rearrangeAuthorities(Map.of(role.getFullName(), role));

    assertEquals(List.of("systemSetting.roleManagement"), fullNames(result));
    assertEquals("角色", result.get(0).getTitle());
    assertEquals(Integer.valueOf(210), result.get(0).getOrder());
    assertEquals("systemSetting", result.get(0).getParent());
    assertInstanceOf(ArrangedModuleAuthority.class, result.get(0));
  }

  @Test
  void shouldDeduplicateSameModuleReferences() {
    var authority = authority("system.read", "读取");
    var integrator = integrator(module("module", "模块", 10,
        List.of(authority.getFullName(), authority.getFullName())));

    var result = integrator.rearrangeAuthorities(Map.of(authority.getFullName(), authority));

    assertEquals(List.of("system.read"), fullNames(result));
  }

  @Test
  void shouldPreferRealAuthorityOverPlacementWithSameFullName() {
    var authority = authority("system", "真实系统");
    var integrator = integrator(
        module("system", "占位系统", 1, List.of()),
        module("system.menu", "系统菜单", 20, List.of(authority.getFullName())));
    authority.setApis(List.of("/api/system"));

    var result = integrator.rearrangeAuthorities(Map.of(authority.getFullName(), authority));

    var selected = result.stream().filter(item -> item.getFullName().equals("system")).findFirst().orElseThrow();
    assertInstanceOf(ArrangedModuleAuthority.class, selected);
    assertEquals(List.of("/api/system"), selected.getApis());
    assertEquals("真实系统", selected.getTitle());
  }

  @Test
  void shouldAddExistingNonEmptyParentWhenItHasNoVisibleAuthority() {
    var role = authority("domain.role", "业务角色");
    var integrator = integrator(
        moduleWithParent("domain.group", "业务分组", "domain", 20, List.of("missing.authority")),
        moduleWithParent("domain.role", "业务角色", "domain.group", 30, List.of(role.getFullName())));

    var result = integrator.rearrangeAuthorities(Map.of(role.getFullName(), role));

    var byName = byFullName(result);
    assertEquals(List.of("domain.group", "domain.role"), fullNames(result));
    assertInstanceOf(PlacementModuleAuthority.class, byName.get("domain.group"));
    assertEquals("domain.group", byName.get("domain.role").getParent());
  }

  @Test
  void shouldKeepEmptyParentAndMovedAuthorityConnected() {
    var role = authority("domain.role", "业务角色");
    var integrator = integrator(
        module("domain.group", "业务分组", 20, List.of()),
        moduleWithParent("domain.role", "业务角色", "domain.group", 30, List.of(role.getFullName())));

    var result = integrator.rearrangeAuthorities(Map.of(role.getFullName(), role));

    var byName = byFullName(result);
    assertInstanceOf(PlacementModuleAuthority.class, byName.get("domain.group"));
    assertEquals("domain.group", byName.get("domain.role").getParent());
  }

  @Test
  void shouldRecursivelyAddMissingModuleAncestors() {
    var role = authority("root.group.role", "角色");
    var integrator = integrator(
        moduleWithParent("root", "根", null, 1, List.of("missing.root")),
        moduleWithParent("root.group", "分组", "root", 2, List.of("missing.group")),
        moduleWithParent("root.group.role", "角色", "root.group", 3, List.of(role.getFullName())));

    var result = integrator.rearrangeAuthorities(Map.of(role.getFullName(), role));

    var byName = byFullName(result);
    assertEquals(3, result.size());
    assertInstanceOf(PlacementModuleAuthority.class, byName.get("root"));
    assertInstanceOf(PlacementModuleAuthority.class, byName.get("root.group"));
    assertEquals("root.group", byName.get("root.group.role").getParent());
  }

  @Test
  void shouldUseLowestOrderForAuthorityWithOnlyExternalReferences() {
    var shared = authority("shared.permission", "共享权限");
    var integrator = integrator(
        module("module.high", "高序模块", 50, List.of(shared.getFullName())),
        module("module.low", "低序模块", 10, List.of(shared.getFullName())));

    var result = integrator.rearrangeAuthorities(Map.of(shared.getFullName(), shared));

    assertEquals(List.of("shared.permission"), fullNames(result));
    assertEquals("共享权限", result.get(0).getTitle());
    assertEquals("shared", result.get(0).getParent());
    assertEquals(Integer.valueOf(10), result.get(0).getOrder());
  }

  @Test
  void shouldFilterUnknownReferences() {
    var known = authority("known.permission", "已知权限");
    var integrator = integrator(module("module", "模块", 1,
        List.of("missing.permission", known.getFullName())));

    var result = integrator.rearrangeAuthorities(Map.of(known.getFullName(), known));

    assertEquals(List.of("known.permission"), fullNames(result));
    assertTrue(result.stream().allMatch(item -> !item.getFullName().equals("missing.permission")));
  }

  private AppModuleIntegrator integrator(AppModule... modules) {
    return () -> List.of(modules);
  }

  private BaseAppModule module(String fullName, String title, int order, List<String> authorities) {
    return moduleWithParent(fullName, title, null, order, authorities);
  }

  private BaseAppModule moduleWithParent(String fullName, String title, String parent, int order,
      List<String> authorities) {
    return new BaseAppModule()
        .setFullName(fullName)
        .setParent(parent)
        .setTitle(title)
        .setOrder(order)
        .setAuthorities(authorities);
  }

  private DefaultAuthority authority(String fullName, String title) {
    var authority = new DefaultAuthority();
    authority.setFullName(fullName);
    authority.setTitle(title);
    return authority;
  }

  private List<String> fullNames(List<Authority> authorities) {
    return authorities.stream().map(Authority::getFullName).collect(Collectors.toList());
  }

  private Map<String, Authority> byFullName(List<Authority> authorities) {
    return authorities.stream().collect(Collectors.toMap(Authority::getFullName, Function.identity()));
  }
}
