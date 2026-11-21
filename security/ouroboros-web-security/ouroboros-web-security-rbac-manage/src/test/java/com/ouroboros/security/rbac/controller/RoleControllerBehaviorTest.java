package com.ouroboros.security.rbac.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.RoleCenter;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.entity.RoleEntity;
import com.ouroboros.security.rbac.entity.UserRoleMapEntity;
import com.ouroboros.security.rbac.model.RbacRoleModel;
import com.ouroboros.security.rbac.service.RoleAuthorityService;
import com.ouroboros.security.rbac.service.RoleService;
import com.ouroboros.security.rbac.service.UserRoleService;
import com.ouroboros.util.SPI;

class RoleControllerBehaviorTest {

  @org.junit.jupiter.api.AfterEach
  void clearSpi() {
    clearSpiPrimary(RoleCenter.class);
  }

  @Test
  void shouldHandleRoleCrudAndMemberEndpoints() throws Exception {
    var roleService = mock(RoleService.class);
    var userRoleService = mock(UserRoleService.class);
    var roleAuthorityService = mock(RoleAuthorityService.class);
    var userCenter = mock(UserCenter.class);
    var roleCenter = mock(RoleCenter.class);

    var role = new RoleEntity();
    role.setId("r1");
    role.setName("Role 1");

    var roleModel = new SimpleRole("r1", "Role 1");

    when(roleService.add(any(RoleEntity.class))).thenReturn(role);
    when(roleService.update(eq("r1"), any(RoleEntity.class))).thenReturn(true);
    when(roleService.getById("r1")).thenReturn(Optional.of(role));
    when(roleCenter.getRole("r1")).thenReturn(Optional.empty(), Optional.<Role>of(roleModel));
    when(roleCenter.getAllRoles()).thenReturn(Collections.<Role>singletonList(roleModel));

    var mapEntity = new RoleAuthorityMapEntity();
    mapEntity.setId("m1");
    mapEntity.setRole("r1");
    mapEntity.setAuthority("system.read");
    when(roleAuthorityService.getByRoleId("r1")).thenReturn(Collections.singletonList(mapEntity));

    when(userRoleService.searchListByRoleId("r1")).thenReturn(Collections.singletonList(new UserRoleMapEntity("u1", "r1")));
    when(userCenter.getUsers(Collections.singletonList("u1"))).thenReturn(Collections.<User>emptyList());
    when(userRoleService.deleteByRoleIdAndUserIdentities(eq("r1"), any())).thenReturn(true);

    var controller = new RoleController();
    ReflectionTestUtils.setField(controller, "service", roleService);
    ReflectionTestUtils.setField(controller, "userRoleService", userRoleService);
    ReflectionTestUtils.setField(controller, "roleAuthorityService", roleAuthorityService);
    ReflectionTestUtils.setField(controller, "userCenter", userCenter);

    var mvc = MockMvcBuilders.standaloneSetup(controller).build();

    setSpiPrimary(RoleCenter.class, roleCenter);

    mvc.perform(post("/api/sys/role")
            .contentType("application/json")
            .content("{\"id\":\"r1\",\"name\":\"Role 1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value("r1"));

    mvc.perform(put("/api/sys/role/r1")
            .contentType("application/json")
            .content("{\"id\":\"r1\",\"name\":\"Role 1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("r1"));

    mvc.perform(get("/api/sys/role/r1/authority"))
        .andExpect(status().isOk());

    mvc.perform(put("/api/sys/role/r1/authority")
            .contentType("application/json")
            .content("[{\"id\":\"m1\",\"authority\":\"system.read\"}]"))
        .andExpect(status().isOk());

    mvc.perform(get("/api/sys/role/r1/user"))
        .andExpect(status().isOk());

    mvc.perform(put("/api/sys/role/r1/user")
            .contentType("application/json")
            .content("[\"u1\",\"u2\"]"))
        .andExpect(status().isOk());
  }

  @Test
  void shouldTolerateRoleWithoutCreatedAtWhenSearching() throws Exception {
    // 需两条以上记录才会触发排序比较；角色由多个 Provider 聚合而来，
    // 个别记录时间字段缺失不应让整个列表接口失败
    var plainRole = new SimpleRole("r1", "Role 1");

    var roleEntity = new RoleEntity();
    roleEntity.setId("r2");
    roleEntity.setName("Role 2");
    var roleWithoutCreatedAt = new RbacRoleModel(roleEntity, Collections.<RoleAuthorityMapEntity>emptyList());

    var roleCenter = mock(RoleCenter.class);
    when(roleCenter.getAllRoles()).thenReturn(java.util.Arrays.<Role>asList(plainRole, roleWithoutCreatedAt));

    var controller = new RoleController();
    ReflectionTestUtils.setField(controller, "service", mock(RoleService.class));
    ReflectionTestUtils.setField(controller, "userRoleService", mock(UserRoleService.class));
    ReflectionTestUtils.setField(controller, "roleAuthorityService", mock(RoleAuthorityService.class));
    ReflectionTestUtils.setField(controller, "userCenter", mock(UserCenter.class));

    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(
            new RoleControllerHttpStatusTest.BusinessExceptionAdvice(),
            new PlatformExceptionAdvice(),
            new com.ouroboros.web.WebApiResultHttpStatusAdvice())
        .build();

    setSpiPrimary(RoleCenter.class, roleCenter);

    mvc.perform(get("/api/sys/role").param("page", "1").param("perPage", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2))
        .andExpect(jsonPath("$.data.items.length()").value(2));
  }

  @Test
  void shouldCoverGetSearchAuthorityFallbackAndPutUserFailure() throws Exception {
    var roleService = mock(RoleService.class);
    var userRoleService = mock(UserRoleService.class);
    var roleAuthorityService = mock(RoleAuthorityService.class);
    var userCenter = mock(UserCenter.class);

    var role = new RoleEntity();
    role.setId("r1");
    role.setName("Role 1");

    when(roleService.getById("r1")).thenReturn(Optional.of(role));
    when(roleAuthorityService.getByRoleId("r1")).thenReturn(Collections.<RoleAuthorityMapEntity>emptyList());
    when(userRoleService.searchListByRoleId("r1")).thenReturn(Collections.singletonList(new UserRoleMapEntity("u1", "r1")));
    when(userCenter.getUsers(Collections.singletonList("u1"))).thenReturn(Collections.<User>emptyList());
    when(userRoleService.deleteByRoleIdAndUserIdentities(eq("r1"), any())).thenReturn(false);

    var roleCenter = mock(RoleCenter.class);
    var roleModel = new SimpleRole("r1", "Role 1");
    when(roleCenter.getRole("r1")).thenReturn(Optional.<Role>of(roleModel));
    when(roleCenter.getAllRoles()).thenReturn(Collections.<Role>singletonList(roleModel));

    var controller = new RoleController();
    ReflectionTestUtils.setField(controller, "service", roleService);
    ReflectionTestUtils.setField(controller, "userRoleService", userRoleService);
    ReflectionTestUtils.setField(controller, "roleAuthorityService", roleAuthorityService);
    ReflectionTestUtils.setField(controller, "userCenter", userCenter);

    var mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(
            new RoleControllerHttpStatusTest.BusinessExceptionAdvice(),
            new PlatformExceptionAdvice(),
            new com.ouroboros.web.WebApiResultHttpStatusAdvice())
        .build();

    setSpiPrimary(RoleCenter.class, roleCenter);

    mvc.perform(get("/api/sys/role/r1")).andExpect(status().isOk());
    mvc.perform(get("/api/sys/role").param("name", "Role").param("page", "1").param("perPage", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());

    mvc.perform(get("/api/sys/role/r1/authority"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());

    mvc.perform(put("/api/sys/role/r1/user")
            .contentType("application/json")
            .content("[]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.msg").value("更新角色用户失败"));
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

  private static class SimpleRole implements Role {
    private final String id;
    private final String name;

    private SimpleRole(String id, String name) {
      this.id = id;
      this.name = name;
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
    public java.util.List<? extends com.ouroboros.security.authority.OuroborosGrantedAuthority> getAuthorities() {
      return Collections.emptyList();
    }
  }

  @org.springframework.web.bind.annotation.RestControllerAdvice
  static class PlatformExceptionAdvice {
    @org.springframework.web.bind.annotation.ExceptionHandler(com.ouroboros.exception.PlatformException.class)
    public com.ouroboros.web.WebApiResult<?> handle(com.ouroboros.exception.PlatformException exception) {
      return com.ouroboros.web.WebApiResult.error(exception.getMessage());
    }
  }
}
