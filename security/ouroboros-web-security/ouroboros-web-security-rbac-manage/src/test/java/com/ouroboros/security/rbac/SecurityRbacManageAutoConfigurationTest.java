package com.ouroboros.security.rbac;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.entity.UserRoleMapEntity;
import com.ouroboros.security.rbac.provider.AdministratorRoleProvider;
import com.ouroboros.security.rbac.service.UserRoleService;
import com.ouroboros.security.rbac.service.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityRbacManageAutoConfigurationTest {

  @AfterEach
  void clearSecurityContext() {
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void initUserShouldReturnImmediatelyWhenAutoCreateDisabled() {
    var config = new SecurityRbacManageAutoConfiguration();
    var userService = mock(UserService.class);
    var userRoleService = mock(UserRoleService.class);
    var administratorRoleProvider = mock(AdministratorRoleProvider.class);

    ReflectionTestUtils.setField(config, "autoCreateUser", false);
    ReflectionTestUtils.setField(config, "userService", userService);
    ReflectionTestUtils.setField(config, "userRoleService", userRoleService);
    ReflectionTestUtils.setField(config, "administratorRoleProvider", administratorRoleProvider);

    config.initUser(mock(OuroborosContextRefreshedEvent.class));

    verify(userService, never()).searchAll();
    verify(userService, never()).add(any(UserEntity.class));
  }

  @Test
  void initUserShouldResetConfiguredPasswordWhenAutoUserAlreadyExists() {
    var config = new SecurityRbacManageAutoConfiguration();
    var userService = mock(UserService.class);
    var userRoleService = mock(UserRoleService.class);
    var administratorRoleProvider = mock(AdministratorRoleProvider.class);

    UserEntity admin = new UserEntity();
    admin.setUserIdentity("admin");
    when(userService.getByUserIdentity("admin")).thenReturn(Optional.of(admin));
    when(administratorRoleProvider.getAdministratorRoleId()).thenReturn("ADMIN");
    when(userRoleService.getByRoleIdAndUserIdentity("ADMIN", "admin"))
        .thenReturn(Optional.of(new UserRoleMapEntity("admin", "ADMIN")));

    ReflectionTestUtils.setField(config, "autoCreateUser", true);
    ReflectionTestUtils.setField(config, "autoCreateUserName", "admin");
    ReflectionTestUtils.setField(config, "autoCreateUserPassword", "fixed-pwd");
    ReflectionTestUtils.setField(config, "userService", userService);
    ReflectionTestUtils.setField(config, "userRoleService", userRoleService);
    ReflectionTestUtils.setField(config, "administratorRoleProvider", administratorRoleProvider);

    config.initUser(mock(OuroborosContextRefreshedEvent.class));

    verify(userService).getByUserIdentity("admin");
    verify(userService).modifyPassword("admin", "fixed-pwd");
    verify(userService, never()).add(any(UserEntity.class));
    verify(userRoleService, never()).add(any(UserRoleMapEntity.class));
    assertFalse(CurrentSecurityContextHolder.findIdentity().isPresent());
  }

  @Test
  void initUserShouldCreateConfiguredAdminWhenOtherUsersAlreadyExist() {
    var config = new SecurityRbacManageAutoConfiguration();
    var userService = mock(UserService.class);
    var userRoleService = mock(UserRoleService.class);
    var administratorRoleProvider = mock(AdministratorRoleProvider.class);

    when(userService.searchAll()).thenReturn(Collections.singletonList(new UserEntity()));
    when(userService.getByUserIdentity("admin")).thenReturn(Optional.empty());
    when(administratorRoleProvider.getAdministratorRoleId()).thenReturn("ADMIN");
    when(userRoleService.getByRoleIdAndUserIdentity("ADMIN", "admin")).thenReturn(Optional.empty());

    ReflectionTestUtils.setField(config, "autoCreateUser", true);
    ReflectionTestUtils.setField(config, "autoCreateUserName", "admin");
    ReflectionTestUtils.setField(config, "autoCreateUserPassword", "fixed-pwd");
    ReflectionTestUtils.setField(config, "userService", userService);
    ReflectionTestUtils.setField(config, "userRoleService", userRoleService);
    ReflectionTestUtils.setField(config, "administratorRoleProvider", administratorRoleProvider);

    config.initUser(mock(OuroborosContextRefreshedEvent.class));

    verify(userService, never()).searchAll();
    verify(userService).add(any(UserEntity.class));
    verify(userRoleService).add(any(UserRoleMapEntity.class));
    assertFalse(CurrentSecurityContextHolder.findIdentity().isPresent());
  }

  @Test
  void initUserShouldCreateAdminAndRoleWhenNoUsersAndPasswordProvided() {
    var config = new SecurityRbacManageAutoConfiguration();
    var userService = mock(UserService.class);
    var userRoleService = mock(UserRoleService.class);
    var administratorRoleProvider = mock(AdministratorRoleProvider.class);

    when(userService.getByUserIdentity("admin")).thenReturn(Optional.empty());
    when(administratorRoleProvider.getAdministratorRoleId()).thenReturn("ADMIN");
    when(userRoleService.getByRoleIdAndUserIdentity("ADMIN", "admin")).thenReturn(Optional.empty());
    when(userService.add(any(UserEntity.class))).thenAnswer(invocation -> {
      assertEquals(SystemSecurityIdentities.SYSTEM_USER_ID,
          CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
      return invocation.getArgument(0);
    });
    when(userRoleService.add(any(UserRoleMapEntity.class))).thenAnswer(invocation -> {
      assertEquals(SystemSecurityIdentities.SYSTEM_USER_ID,
          CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
      return invocation.getArgument(0);
    });

    ReflectionTestUtils.setField(config, "autoCreateUser", true);
    ReflectionTestUtils.setField(config, "autoCreateUserName", "admin");
    ReflectionTestUtils.setField(config, "autoCreateUserPassword", "fixed-pwd");
    ReflectionTestUtils.setField(config, "userService", userService);
    ReflectionTestUtils.setField(config, "userRoleService", userRoleService);
    ReflectionTestUtils.setField(config, "administratorRoleProvider", administratorRoleProvider);

    config.initUser(mock(OuroborosContextRefreshedEvent.class));

    verify(userService).add(any(UserEntity.class));
    verify(userRoleService).add(any(UserRoleMapEntity.class));
    assertFalse(CurrentSecurityContextHolder.findIdentity().isPresent());
  }

  @Test
  void initUserShouldGeneratePasswordWhenNotConfiguredAndSwallowExceptions() {
    var config = new SecurityRbacManageAutoConfiguration();
    var userService = mock(UserService.class);
    var userRoleService = mock(UserRoleService.class);
    var administratorRoleProvider = mock(AdministratorRoleProvider.class);

    when(userService.getByUserIdentity("admin")).thenReturn(Optional.empty());
    when(administratorRoleProvider.getAdministratorRoleId()).thenReturn("ADMIN");
    when(userService.add(any(UserEntity.class))).thenThrow(new RuntimeException("boom"));

    ReflectionTestUtils.setField(config, "autoCreateUser", true);
    ReflectionTestUtils.setField(config, "autoCreateUserName", "admin");
    ReflectionTestUtils.setField(config, "autoCreateUserPassword", "");
    ReflectionTestUtils.setField(config, "userService", userService);
    ReflectionTestUtils.setField(config, "userRoleService", userRoleService);
    ReflectionTestUtils.setField(config, "administratorRoleProvider", administratorRoleProvider);

    config.initUser(mock(OuroborosContextRefreshedEvent.class));

    verify(userService).add(any(UserEntity.class));
    verify(userRoleService, never()).add(any(UserRoleMapEntity.class));
    assertFalse(CurrentSecurityContextHolder.findIdentity().isPresent());
  }

  @Test
  void initShouldBeCallable() {
    var config = new SecurityRbacManageAutoConfiguration();
    config.init();
    assertNotNull(config);
  }
}
