package com.ouroboros.security.rbac.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.RoleCenter;
import com.ouroboros.security.rbac.RoleProvider;
import com.ouroboros.security.rbac.model.BuiltinRoleAuthorityModel;
import com.ouroboros.security.rbac.model.BuiltinRoleModel;
import com.ouroboros.security.rbac.store.BuiltinRoleDefinitionStore;
import com.ouroboros.util.Json;

class BuiltinRoleControllerBehaviorTest {

  @TempDir
  Path appHome;

  @AfterEach
  void clearSpi() throws Exception {
    clearSpiPrimary(RoleCenter.class);
    clearSpiPrimary(RoleProvider.class);
  }

  @Test
  void builtinRoleShouldAllowAuthorityEditsAndMembershipChangesButRejectDeletes() throws Exception {
    BuiltinRoleDefinitionStore store = new BuiltinRoleDefinitionStore(() -> appHome.toString());
    Files.createDirectories(appHome.resolve("rbac/builtin-roles"));
    Files.write(appHome.resolve("rbac/builtin-roles/builtin-reviewer.role.json"),
        Json.toPrettyJsonString(builtinRole()).getBytes(StandardCharsets.UTF_8));

    BuiltinRoleModel builtinRole = builtinRole();
    setSpiPrimary(RoleCenter.class, new RoleCenter() {
      @Override
      public Optional<Role> getRole(String id) {
        return "builtin-reviewer".equals(id) ? Optional.<Role>of(builtinRole) : Optional.empty();
      }

      @Override
      public java.util.List<Role> getRoles(Collection<String> ids) {
        List<Role> roles = new ArrayList<Role>();
        if (ids != null && ids.contains("builtin-reviewer")) {
          roles.add(builtinRole);
        }
        return roles;
      }

      @Override
      public java.util.List<Role> getAllRoles() {
        return Collections.<Role>singletonList(builtinRole);
      }
    });

    RoleController controller = new RoleController();
    ReflectionTestUtils.setField(controller, "builtinRoleDefinitionStore", store);
    ReflectionTestUtils.setField(controller, "service", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.RoleService.class));
    ReflectionTestUtils.setField(controller, "userRoleService", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.UserRoleService.class));
    ReflectionTestUtils.setField(controller, "roleAuthorityService", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.RoleAuthorityService.class));
    ReflectionTestUtils.setField(controller, "userCenter", org.mockito.Mockito.mock(com.ouroboros.security.rbac.UserCenter.class));

    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new RoleControllerHttpStatusTest.BusinessExceptionAdvice(),
            new RoleControllerBehaviorTest.PlatformExceptionAdvice(),
            new com.ouroboros.web.WebApiResultHttpStatusAdvice())
        .build();

    mvc.perform(put("/api/sys/role/builtin-reviewer/authority")
            .contentType("application/json")
            .content("[{\"authority\":\"workflow.approve\",\"accessControlParams\":{}}]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].authority").value("workflow.approve"));

    assertTrue(store.findById("builtin-reviewer").isPresent());
    assertTrue(store.findById("builtin-reviewer").get().getAuthorityDefinitions().size() == 1);

    mvc.perform(delete("/api/sys/role/builtin-reviewer"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.msg").value("内置角色不允许删除"));

    mvc.perform(put("/api/sys/role/builtin-reviewer/user")
            .contentType("application/json")
            .content("[\"user-1\"]"))
        .andExpect(status().isOk());
  }

  @Test
  void putAuthorityShouldRejectInvalidAuthorityPayloads() throws Exception {
    BuiltinRoleDefinitionStore store = new BuiltinRoleDefinitionStore(() -> appHome.toString());
    Files.createDirectories(appHome.resolve("rbac/builtin-roles"));
    Files.write(appHome.resolve("rbac/builtin-roles/builtin-reviewer.role.json"),
        Json.toPrettyJsonString(builtinRole()).getBytes(StandardCharsets.UTF_8));

    BuiltinRoleModel builtinRole = builtinRole();
    setSpiPrimary(RoleCenter.class, new SingleRoleCenter(builtinRole));

    RoleController controller = new RoleController();
    ReflectionTestUtils.setField(controller, "builtinRoleDefinitionStore", store);
    ReflectionTestUtils.setField(controller, "service", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.RoleService.class));
    ReflectionTestUtils.setField(controller, "userRoleService", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.UserRoleService.class));
    ReflectionTestUtils.setField(controller, "roleAuthorityService", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.RoleAuthorityService.class));
    ReflectionTestUtils.setField(controller, "userCenter", org.mockito.Mockito.mock(com.ouroboros.security.rbac.UserCenter.class));

    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new RoleControllerHttpStatusTest.BusinessExceptionAdvice(),
            new RoleControllerBehaviorTest.PlatformExceptionAdvice(),
            new com.ouroboros.web.WebApiResultHttpStatusAdvice())
        .build();

    mvc.perform(put("/api/sys/role/builtin-reviewer/authority")
            .contentType("application/json")
            .content("[{\"authority\":\"\",\"accessControlParams\":{}}]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.msg").value("权限标识不能为空: 第1项"));

    mvc.perform(put("/api/sys/role/builtin-reviewer/authority")
            .contentType("application/json")
            .content("[{\"authority\":\"workflow.approve\"},{\"authority\":\"workflow.approve\"}]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.msg").value("权限标识重复: workflow.approve"));
  }

  @Test
  void putAuthorityShouldAcceptSerializedAccessControlParams() throws Exception {
    BuiltinRoleDefinitionStore store = new BuiltinRoleDefinitionStore(() -> appHome.toString());
    Files.createDirectories(appHome.resolve("rbac/builtin-roles"));
    Files.write(appHome.resolve("rbac/builtin-roles/builtin-reviewer.role.json"),
        Json.toPrettyJsonString(builtinRole()).getBytes(StandardCharsets.UTF_8));

    BuiltinRoleModel builtinRole = builtinRole();
    setSpiPrimary(RoleCenter.class, new SingleRoleCenter(builtinRole));

    RoleController controller = new RoleController();
    ReflectionTestUtils.setField(controller, "builtinRoleDefinitionStore", store);
    ReflectionTestUtils.setField(controller, "service", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.RoleService.class));
    ReflectionTestUtils.setField(controller, "userRoleService", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.UserRoleService.class));
    ReflectionTestUtils.setField(controller, "roleAuthorityService", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.RoleAuthorityService.class));
    ReflectionTestUtils.setField(controller, "userCenter", org.mockito.Mockito.mock(com.ouroboros.security.rbac.UserCenter.class));

    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new RoleControllerHttpStatusTest.BusinessExceptionAdvice(),
            new RoleControllerBehaviorTest.PlatformExceptionAdvice(),
            new com.ouroboros.web.WebApiResultHttpStatusAdvice())
        .build();

    // 对象形态（既有契约）
    mvc.perform(put("/api/sys/role/builtin-reviewer/authority")
            .contentType("application/json")
            .content("[{\"authority\":\"workflow.approve\","
                + "\"accessControlParams\":{\"allowedFields\":[\"*\"],\"disallowedFields\":[],\"filters\":{}}}]"))
        .andExpect(status().isOk());

    var objectFormParams = store.findById("builtin-reviewer").orElseThrow()
        .getAuthorityDefinitions().get(0).getAccessControlParamsSetting();

    // 字符串形态（本次兼容目标）应与对象形态等价，而不是报「角色权限数据非法」
    mvc.perform(put("/api/sys/role/builtin-reviewer/authority")
            .contentType("application/json")
            .content("[{\"authority\":\"workflow.approve\","
                + "\"accessControlParams\":\"{\\\"allowedFields\\\":[\\\"*\\\"],\\\"disallowedFields\\\":[],\\\"filters\\\":{}}\"}]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].authority").value("workflow.approve"));

    var stringFormParams = store.findById("builtin-reviewer").orElseThrow()
        .getAuthorityDefinitions().get(0).getAccessControlParamsSetting();
    assertTrue(stringFormParams.containsKey("allowedFields"),
        "字符串形态应被解析为对象并持久化，实际=" + stringFormParams);
    assertTrue(objectFormParams.equals(stringFormParams),
        "字符串形态应与对象形态等价，对象=" + objectFormParams + " 字符串=" + stringFormParams);

    // 非 JSON 字符串仍应被拒绝，且给出明确的非法提示
    mvc.perform(put("/api/sys/role/builtin-reviewer/authority")
            .contentType("application/json")
            .content("[{\"authority\":\"workflow.approve\",\"accessControlParams\":\"not-a-json\"}]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.msg").value("角色权限数据非法: 第1项"));
  }

  @Test
  void putAuthorityShouldRejectRolesThatCannotEditAuthorities() throws Exception {
    BuiltinRoleDefinitionStore store = new BuiltinRoleDefinitionStore(() -> appHome.toString());
    var administratorRole = new com.ouroboros.security.rbac.model.AdministratorRoleModel();
    setSpiPrimary(RoleCenter.class, new SingleRoleCenter(administratorRole));

    RoleController controller = new RoleController();
    ReflectionTestUtils.setField(controller, "builtinRoleDefinitionStore", store);
    ReflectionTestUtils.setField(controller, "service", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.RoleService.class));
    ReflectionTestUtils.setField(controller, "userRoleService", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.UserRoleService.class));
    ReflectionTestUtils.setField(controller, "roleAuthorityService", org.mockito.Mockito.mock(com.ouroboros.security.rbac.service.RoleAuthorityService.class));
    ReflectionTestUtils.setField(controller, "userCenter", org.mockito.Mockito.mock(com.ouroboros.security.rbac.UserCenter.class));

    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new RoleControllerHttpStatusTest.BusinessExceptionAdvice(),
            new RoleControllerBehaviorTest.PlatformExceptionAdvice(),
            new com.ouroboros.web.WebApiResultHttpStatusAdvice())
        .build();

    mvc.perform(put("/api/sys/role/administrator/authority")
            .contentType("application/json")
            .content("[{\"authority\":\"workflow.approve\"}]"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403))
        .andExpect(jsonPath("$.msg").value("角色不允许修改权限"));
  }

  private BuiltinRoleModel builtinRole() {
    BuiltinRoleModel model = new BuiltinRoleModel();
    model.setId("builtin-reviewer");
    model.setName("内置审阅者");
    BuiltinRoleAuthorityModel authority = new BuiltinRoleAuthorityModel();
    authority.setAuthority("workflow.approve");
    authority.setAccessControlParams(Collections.<String, Object>emptyMap());
    model.setAuthorities(Collections.singletonList(authority));
    return model;
  }

  private static class SingleRoleCenter implements RoleCenter {
    private final Role role;

    private SingleRoleCenter(Role role) {
      this.role = role;
    }

    @Override
    public Optional<Role> getRole(String id) {
      return role != null && role.getId().equals(id) ? Optional.of(role) : Optional.empty();
    }

    @Override
    public java.util.List<Role> getRoles(Collection<String> ids) {
      if (role != null && ids != null && ids.contains(role.getId())) {
        return Collections.singletonList(role);
      }
      return Collections.emptyList();
    }

    @Override
    public java.util.List<Role> getAllRoles() {
      return role == null ? Collections.emptyList() : Collections.singletonList(role);
    }
  }

  private static org.mockito.MockedStatic<com.ouroboros.util.SPI> spiBinding;

  private static <T> void setSpiPrimary(Class<T> serviceClass, T service) {
    if (spiBinding == null) spiBinding = org.mockito.Mockito.mockStatic(com.ouroboros.util.SPI.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    spiBinding.when(() -> com.ouroboros.util.SPI.getCachedServiceStream(serviceClass))
        .thenAnswer(ignored -> java.util.stream.Stream.of(service));
  }

  private static void clearSpiPrimary(Class<?> serviceClass) {
    if (spiBinding != null) { spiBinding.close(); spiBinding = null; }
    com.ouroboros.util.SPI.clearCache();
  }
}
