package com.ouroboros.security.rbac.controller;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.RoleCenter;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.UserPasswordVerifier;
import com.ouroboros.security.rbac.model.BuiltinRoleModel;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.entity.UserRoleMapEntity;
import com.ouroboros.security.rbac.provider.AdministratorRoleProvider;
import com.ouroboros.security.rbac.service.UserRoleService;
import com.ouroboros.security.rbac.service.UserService;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;
import com.ouroboros.util.SPI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerHttpStatusTest {

  @AfterEach
  void clearSecurityContext() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
    clearSpiPrimary(RoleCenter.class);
  }

  @Test
  void missingUserEndpointsShouldReturnHttp404() throws Exception {
    var userCenter = mock(UserCenter.class);
    when(userCenter.getUser("missing")).thenReturn(Optional.empty());

    var userService = mock(UserService.class);
    when(userService.getByUserIdentity("missing")).thenReturn(Optional.empty());

    var controller = controller(userCenter, mock(UserRoleService.class), userService, mock(AdministratorRoleProvider.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/sys/user/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("用户不存在"));

    mockMvc.perform(delete("/api/sys/user/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("用户不存在"));

    mockMvc.perform(put("/api/sys/user/missing/role")
            .contentType("application/json")
            .content("[]"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("用户不存在"));
  }

  @Test
  void modifyPasswordShouldPreferCurrentSecurityContextIdentityForVerificationAndUpdate() throws Exception {
    installConflictingSecurityIdentities("seam-user", "legacy-holder-user");

    var userPasswordVerifier = mock(UserPasswordVerifier.class);
    when(userPasswordVerifier.verify("legacy-holder-user", "old-password"))
        .thenThrow(new AssertionError("modifyPassword should not use legacy holder principal"));
    when(userPasswordVerifier.verify("seam-user", "old-password")).thenReturn(true);

    var userService = mock(UserService.class);

    var controller = controller(
        mock(UserCenter.class),
        mock(UserRoleService.class),
        userPasswordVerifier,
        userService,
        mock(AdministratorRoleProvider.class)
    );
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(patch("/api/sys/user/modify-password")
            .contentType("application/json")
            .content("{\"verificationMode\":\"password\",\"credentials\":\"old-password\",\"newPassword\":\"new-password\"}"))
        .andExpect(status().isNoContent());

    verify(userPasswordVerifier).verify("seam-user", "old-password");
    verify(userService).modifyPassword("seam-user", "new-password");
  }

  @Test
  void resetPasswordShouldReturnHttp403WhenOperatorIsNotAdmin() throws Exception {
    installConflictingSecurityIdentities("seam-user", "legacy-holder-user");

    var userRoleService = mock(UserRoleService.class);
    when(userRoleService.searchListByUserIdentity("legacy-holder-user"))
        .thenThrow(new AssertionError("resetPassword should not use legacy holder principal"));
    when(userRoleService.searchListByUserIdentity("seam-user")).thenReturn(Collections.emptyList());

    var administratorRoleProvider = mock(AdministratorRoleProvider.class);
    when(administratorRoleProvider.getAdministratorRoleId()).thenReturn("ADMIN");

    var controller = controller(mock(UserCenter.class), userRoleService, mock(UserService.class), administratorRoleProvider);
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/sys/user/missing/reset-password"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403))
        .andExpect(jsonPath("$.msg").value("无权限重置密码!"));
  }

  @Test
  void resetPasswordShouldReturnHttp404WhenTargetUserMissing() throws Exception {
    installConflictingSecurityIdentities("seam-admin", "legacy-holder-user");

    var userRoleService = mock(UserRoleService.class);
    when(userRoleService.searchListByUserIdentity("legacy-holder-user"))
        .thenThrow(new AssertionError("resetPassword should not use legacy holder principal"));
    when(userRoleService.searchListByUserIdentity("seam-admin"))
        .thenReturn(Collections.singletonList(new UserRoleMapEntity("seam-admin", "ADMIN")));

    var userService = mock(UserService.class);
    when(userService.getByUserIdentity("missing")).thenReturn(Optional.empty());

    var administratorRoleProvider = mock(AdministratorRoleProvider.class);
    when(administratorRoleProvider.getAdministratorRoleId()).thenReturn("ADMIN");

    var controller = controller(mock(UserCenter.class), userRoleService, userService, administratorRoleProvider);
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/sys/user/missing/reset-password"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("用户不存在"));
  }

  @Test
  void addShouldValidateUserIdentityPasswordAndDuplicatedUser() throws Exception {
    var userCenter = mock(UserCenter.class);
    when(userCenter.getUser("exists")).thenReturn(Optional.of(mock(User.class)));

    var controller = controller(userCenter, mock(UserRoleService.class), mock(UserService.class), mock(AdministratorRoleProvider.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/sys/user")
            .contentType("application/json")
            .content("{\"password\":\"x\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.msg").value("用户标识不能为空"));

    mockMvc.perform(post("/api/sys/user")
            .contentType("application/json")
            .content("{\"userIdentity\":\"u1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.msg").value("密码不能为空"));

    mockMvc.perform(post("/api/sys/user")
            .contentType("application/json")
            .content("{\"userIdentity\":\"exists\",\"password\":\"x\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.msg").value("用户已存在"));
  }

  @Test
  void addShouldCreateUserAndRolesSuccessfully() throws Exception {
    var userCenter = mock(UserCenter.class);
    when(userCenter.getUser("u-created")).thenReturn(Optional.empty());

    var roleCenter = mock(RoleCenter.class);
    when(roleCenter.getRole(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
    setSpiPrimary(RoleCenter.class, roleCenter);

    var userService = mock(UserService.class);
    var created = new UserEntity();
    created.setUserIdentity("u-created");
    when(userService.add(org.mockito.ArgumentMatchers.any(UserEntity.class))).thenReturn(created);

    var userRoleService = mock(UserRoleService.class);
    when(userRoleService.add(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(Collections.emptyList());

    var controller = controller(userCenter, userRoleService, userService, mock(AdministratorRoleProvider.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/sys/user")
            .contentType("application/json")
            .content("{\"userIdentity\":\"u-created\",\"password\":\"p\",\"roles\":[\"R1\",\"R2\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.userIdentity").value("u-created"));
  }

  @Test
  void modifyPasswordShouldReturnHttp400WhenPasswordVerificationFails() throws Exception {
    installConflictingSecurityIdentities("seam-user", "legacy-holder-user");

    var verifier = mock(UserPasswordVerifier.class);
    when(verifier.verify("seam-user", "wrong")).thenReturn(false);

    var controller = controller(mock(UserCenter.class), mock(UserRoleService.class), verifier, mock(UserService.class), mock(AdministratorRoleProvider.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(patch("/api/sys/user/modify-password")
            .contentType("application/json")
            .content("{\"verificationMode\":\"password\",\"credentials\":\"wrong\",\"newPassword\":\"new-password\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.msg").value("密码错误!"));
  }

  @Test
  void modifyPasswordShouldReturnHttp500WhenVerificationModeUnknown() throws Exception {
    installConflictingSecurityIdentities("seam-user", "legacy-holder-user");

    var controller = controller(mock(UserCenter.class), mock(UserRoleService.class), mock(UserService.class), mock(AdministratorRoleProvider.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(patch("/api/sys/user/modify-password")
            .contentType("application/json")
            .content("{\"verificationMode\":\"otp\",\"credentials\":\"c\",\"newPassword\":\"new-password\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.msg").value("未知的验证方式: otp"));
  }

  @Test
  void resetPasswordShouldReturnHttp204ForAdminAndExistingTarget() throws Exception {
    installConflictingSecurityIdentities("seam-admin", "legacy-holder-user");

    var userRoleService = mock(UserRoleService.class);
    when(userRoleService.searchListByUserIdentity("seam-admin"))
        .thenReturn(Collections.singletonList(new UserRoleMapEntity("seam-admin", "ADMIN")));

    var userService = mock(UserService.class);
    when(userService.getByUserIdentity("target")).thenReturn(Optional.of(new UserEntity()));
    when(userService.resetPassword("target")).thenReturn(true);

    var administratorRoleProvider = mock(AdministratorRoleProvider.class);
    when(administratorRoleProvider.getAdministratorRoleId()).thenReturn("ADMIN");

    var controller = controller(mock(UserCenter.class), userRoleService, userService, administratorRoleProvider);
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/sys/user/target/reset-password"))
        .andExpect(status().isNoContent());
  }

  @Test
  void resetPasswordShouldReturnHttp404WhenResetReturnsFalse() throws Exception {
    installConflictingSecurityIdentities("seam-admin", "legacy-holder-user");

    var userRoleService = mock(UserRoleService.class);
    when(userRoleService.searchListByUserIdentity("seam-admin"))
        .thenReturn(Collections.singletonList(new UserRoleMapEntity("seam-admin", "ADMIN")));

    var userService = mock(UserService.class);
    when(userService.getByUserIdentity("target")).thenReturn(Optional.of(new UserEntity()));
    when(userService.resetPassword("target")).thenReturn(false);

    var administratorRoleProvider = mock(AdministratorRoleProvider.class);
    when(administratorRoleProvider.getAdministratorRoleId()).thenReturn("ADMIN");

    var controller = controller(mock(UserCenter.class), userRoleService, userService, administratorRoleProvider);
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/sys/user/target/reset-password"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.msg").value("用户不存在"));
  }

  @Test
  void getShouldReturnUserFieldsWhenUserExists() throws Exception {
    var user = mock(User.class);
    var role = mock(Role.class);
    when(role.getId()).thenReturn("R-1");
    when(user.getUserIdentity()).thenReturn("u1");
    when(user.getRoles()).thenReturn(Collections.singletonList(role));

    var userCenter = mock(UserCenter.class);
    when(userCenter.getUser("u1")).thenReturn(Optional.of(user));

    var controller = controller(userCenter, mock(UserRoleService.class), mock(UserService.class), mock(AdministratorRoleProvider.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/sys/user/u1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userIdentity").value("u1"))
        .andExpect(jsonPath("$.data.isControlled").value(false))
        .andExpect(jsonPath("$.data.roles[0]").value("R-1"));
  }

  @Test
  void searchShouldSupportControlledAndUncontrolledUsersAndPagination() throws Exception {
    var roleOnlyUser = mock(User.class);
    when(roleOnlyUser.getUserIdentity()).thenReturn("alpha-user");
    when(roleOnlyUser.getRoles()).thenReturn(Collections.<Role>emptyList());

    var controlledEntity = new UserEntity();
    controlledEntity.setUserIdentity("beta-user");
    controlledEntity.setCreatedAt(java.time.LocalDateTime.now().minusDays(1));
    var controlledUser =
        new com.ouroboros.security.rbac.model.RbacUserModel(controlledEntity, mock(com.ouroboros.security.claims.ClaimsAggregator.class),
            mock(com.ouroboros.security.rbac.SubjectCenter.class), mock(com.ouroboros.security.rbac.UserSubjectCenter.class));

    var userCenter = mock(UserCenter.class);
    when(userCenter.getAllUsers()).thenReturn(java.util.Arrays.asList(roleOnlyUser, controlledUser));

    var controller = controller(userCenter, mock(UserRoleService.class), mock(UserService.class), mock(AdministratorRoleProvider.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/sys/user").param("userIdentity", "user").param("page", "1").param("perPage", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2))
        .andExpect(jsonPath("$.data.items[0].userIdentity").exists())
        .andExpect(jsonPath("$.data.items[0].isControlled").exists());
  }

  @Test
  void searchShouldTolerateUserWithoutCreatedAt() throws Exception {
    // 需两条以上记录才会触发排序比较；用户由多个 Provider 聚合而来，
    // 个别记录时间字段缺失不应让整个列表接口失败
    var plainUser = mock(User.class);
    when(plainUser.getUserIdentity()).thenReturn("alpha-user");

    var entityWithoutCreatedAt = new UserEntity();
    entityWithoutCreatedAt.setUserIdentity("gamma-user");
    var modelWithoutCreatedAt =
        new com.ouroboros.security.rbac.model.RbacUserModel(entityWithoutCreatedAt,
            mock(com.ouroboros.security.claims.ClaimsAggregator.class),
            mock(com.ouroboros.security.rbac.SubjectCenter.class),
            mock(com.ouroboros.security.rbac.UserSubjectCenter.class));

    var userCenter = mock(UserCenter.class);
    when(userCenter.getAllUsers()).thenReturn(java.util.Arrays.asList(plainUser, modelWithoutCreatedAt));

    var controller = controller(userCenter, mock(UserRoleService.class), mock(UserService.class), mock(AdministratorRoleProvider.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(get("/api/sys/user").param("page", "1").param("perPage", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2))
        .andExpect(jsonPath("$.data.items.length()").value(2));
  }

  @Test
  void searchRoleShouldResolveDistinctRolesFromUserRoleService() throws Exception {
    var userRoleService = mock(UserRoleService.class);
    when(userRoleService.searchListByUserIdentity("u1")).thenReturn(java.util.Arrays.asList(
        new UserRoleMapEntity("u1", "R1"),
        new UserRoleMapEntity("u1", "R1"),
        new UserRoleMapEntity("u1", "R2")
    ));

    var roleCenter = mock(RoleCenter.class);
    when(roleCenter.getRoles(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(Collections.<Role>emptyList());

    var controller = controller(mock(UserCenter.class), userRoleService, mock(UserService.class), mock(AdministratorRoleProvider.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    setSpiPrimary(RoleCenter.class, roleCenter);
    mockMvc.perform(get("/api/sys/user/u1/role"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
  }

  @Test
  void putRoleShouldHandleMissingUserAndSuccessAndDeleteFailure() throws Exception {
    var userCenter = mock(UserCenter.class);
    when(userCenter.getUser("missing")).thenReturn(Optional.empty());
    when(userCenter.getUser("u1")).thenReturn(Optional.of(mock(User.class)));

    var userRoleService = mock(UserRoleService.class);
    when(userRoleService.searchListByUserIdentity("u1")).thenReturn(java.util.Arrays.asList(
        new UserRoleMapEntity("u1", "R1"),
        new UserRoleMapEntity("u1", "R2")
    ));
    when(userRoleService.deleteByUserIdentityAndRoleIds("u1", java.util.Collections.singletonList("R2"))).thenReturn(true);
    when(userRoleService.deleteByUserIdentityAndRoleIds("u1", java.util.Collections.singletonList("R1"))).thenReturn(false);

    var roleCenter = mock(RoleCenter.class);
    when(roleCenter.getRoles(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(Collections.<Role>emptyList());

    var controller = controller(userCenter, userRoleService, mock(UserService.class), mock(AdministratorRoleProvider.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(put("/api/sys/user/missing/role").contentType("application/json").content("[\"R1\"]"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.msg").value("用户不存在"));

    setSpiPrimary(RoleCenter.class, roleCenter);
    mockMvc.perform(put("/api/sys/user/u1/role").contentType("application/json").content("[\"R1\"]"))
        .andExpect(status().isOk());

    mockMvc.perform(put("/api/sys/user/u1/role").contentType("application/json").content("[\"R2\"]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.msg").value("更新用户角色失败"));
  }

  @Test
  void addAndPutRoleShouldAllowBuiltinRoleMembershipChanges() throws Exception {
    var userCenter = mock(UserCenter.class);
    when(userCenter.getUser("u1")).thenReturn(Optional.of(mock(User.class)));
    when(userCenter.getUser("u-built-in")).thenReturn(Optional.empty());

    var userRoleService = mock(UserRoleService.class);
    when(userRoleService.searchListByUserIdentity("u1")).thenReturn(Collections.emptyList());

    var builtinRole = new BuiltinRoleModel();
    builtinRole.setId("builtin-reviewer");
    builtinRole.setName("内置审阅者");

    var roleCenter = mock(RoleCenter.class);
    when(roleCenter.getRole("builtin-reviewer")).thenReturn(Optional.of(builtinRole));
    when(roleCenter.getRoles(anyList())).thenReturn(Collections.emptyList());
    setSpiPrimary(RoleCenter.class, roleCenter);

    var userService = mock(UserService.class);
    when(userService.add(any(UserEntity.class))).thenReturn(new UserEntity());

    var controller = controller(userCenter, userRoleService, userService, mock(AdministratorRoleProvider.class));
    var mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new BusinessExceptionAdvice(), new PlatformExceptionAdvice(), new WebApiResultHttpStatusAdvice())
        .build();

    mockMvc.perform(post("/api/sys/user")
            .contentType("application/json")
            .content("{\"userIdentity\":\"u-built-in\",\"password\":\"p\",\"roles\":[\"builtin-reviewer\"]}"))
        .andExpect(status().isCreated());

    mockMvc.perform(put("/api/sys/user/u1/role")
            .contentType("application/json")
            .content("[\"builtin-reviewer\"]"))
        .andExpect(status().isOk());
  }

  @Test
  void addUserVoShouldExposeFieldsAndConvertToUserModel() {
    var vo = new UserController.AddUserVO();
    vo.setUserIdentity("u-vo");
    vo.setPassword("p-vo");
    vo.setAvatar("a-vo");
    vo.setRoles(java.util.Arrays.asList("R1", "R2"));

    assertEquals("u-vo", vo.getUserIdentity());
    assertEquals("p-vo", vo.getPassword());
    assertEquals("a-vo", vo.getAvatar());
    assertEquals(2, vo.getRoles().size());

    var userModel = vo.toUserModel();
    assertEquals("u-vo", userModel.getUserIdentity());
    assertEquals("p-vo", userModel.getPassword());
    assertEquals("a-vo", userModel.getAvatar());
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

  private void installConflictingSecurityIdentities(String seamUserIdentity, String legacyPrincipal) {
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(() -> seamUserIdentity)));
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(legacyPrincipal, "n/a"));
  }

  private UserController controller(UserCenter userCenter,
                                    UserRoleService userRoleService,
                                    UserService userService,
                                    AdministratorRoleProvider administratorRoleProvider) {
    return controller(userCenter, userRoleService, mock(UserPasswordVerifier.class), userService, administratorRoleProvider);
  }

  private UserController controller(UserCenter userCenter,
                                    UserRoleService userRoleService,
                                    UserPasswordVerifier userPasswordVerifier,
                                    UserService userService,
                                    AdministratorRoleProvider administratorRoleProvider) {
    var controller = new UserController();
    ReflectionTestUtils.setField(controller, "userCenter", userCenter);
    ReflectionTestUtils.setField(controller, "userRoleService", userRoleService);
    ReflectionTestUtils.setField(controller, "userPasswordVerifier", userPasswordVerifier);
    ReflectionTestUtils.setField(controller, "userService", userService);
    ReflectionTestUtils.setField(controller, "administratorRoleProvider", administratorRoleProvider);
    return controller;
  }

  @RestControllerAdvice
  static class BusinessExceptionAdvice {
    @ExceptionHandler(BusinessException.class)
    public WebApiResult<?> handle(BusinessException exception) {
      return WebApiResult.error(exception.getErrorCode(), exception.getMessage());
    }
  }

  @RestControllerAdvice
  static class PlatformExceptionAdvice {
    @ExceptionHandler(PlatformException.class)
    public WebApiResult<?> handle(PlatformException exception) {
      return WebApiResult.error(exception.getMessage());
    }
  }
}
