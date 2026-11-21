package com.ouroboros.workflow.approval.external.runtime.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.UserRoleProvider;

/**
 * M14 权限守卫测试：管理员角色放行、权限点 authority 放行、无权限拒绝。
 *
 * <p>未登录分支依赖 CurrentUserSupplier SPI 返回空，测试类路径固定提供 test-admin，
 * 无法在该模块内构造匿名场景（偏差记录于计划决策日志 D-5）。
 */
class EngineMappingPermissionGuardTest {

  @Test
  void adminRolePasses() {
    UserRoleProvider userRoleProvider = mock(UserRoleProvider.class);
    when(userRoleProvider.getRolesByUser("test-admin")).thenReturn(List.of(role("ADMIN", null)));
    EngineMappingPermissionGuard guard = new EngineMappingPermissionGuard(userRoleProvider, "ADMIN");

    assertThatCode(() -> guard.requirePermission(EngineMappingPermissions.MAPPING_ACTIVATE))
        .doesNotThrowAnyException();
  }

  @Test
  void authorityContainingPermissionPointPasses() {
    UserRoleProvider userRoleProvider = mock(UserRoleProvider.class);
    OuroborosGrantedAuthority pauseAuthority = authority(EngineMappingPermissions.MAPPING_PAUSE);
    when(userRoleProvider.getRolesByUser("test-admin"))
        .thenReturn(List.of(role("ROLE_X", List.of(pauseAuthority))));
    EngineMappingPermissionGuard guard = new EngineMappingPermissionGuard(userRoleProvider, "ADMIN");

    assertThatCode(() -> guard.requirePermission(EngineMappingPermissions.MAPPING_PAUSE))
        .doesNotThrowAnyException();
  }

  @Test
  void roleNamedAsPermissionPointPasses() {
    UserRoleProvider userRoleProvider = mock(UserRoleProvider.class);
    when(userRoleProvider.getRolesByUser("test-admin")).thenReturn(List.of(role(
        EngineMappingPermissions.MAPPING_RETIRE, null)));
    EngineMappingPermissionGuard guard = new EngineMappingPermissionGuard(userRoleProvider, "ADMIN");

    assertThatCode(() -> guard.requirePermission(EngineMappingPermissions.MAPPING_RETIRE))
        .doesNotThrowAnyException();
  }

  @Test
  void userWithoutPermissionIsRejected() {
    UserRoleProvider userRoleProvider = mock(UserRoleProvider.class);
    when(userRoleProvider.getRolesByUser("test-admin")).thenReturn(List.of(role("ROLE_X", null)));
    EngineMappingPermissionGuard guard = new EngineMappingPermissionGuard(userRoleProvider, "ADMIN");

    assertThatThrownBy(() -> guard.requirePermission(EngineMappingPermissions.MAPPING_ACTIVATE))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining(EngineMappingPermissions.MAPPING_ACTIVATE);
  }

  @Test
  void missingRbacRuntimeFailsClosed() {
    EngineMappingPermissionGuard guard = new EngineMappingPermissionGuard(null, "ADMIN");

    assertThatThrownBy(() -> guard.requirePermission(EngineMappingPermissions.MAPPING_VIEW))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("RBAC 未装配")
        .hasMessageContaining(EngineMappingPermissions.MAPPING_VIEW);
  }

  @Test
  void rbacQueryFailureFailsClosed() {
    UserRoleProvider userRoleProvider = mock(UserRoleProvider.class);
    when(userRoleProvider.getRolesByUser("test-admin")).thenThrow(new IllegalStateException("rbac down"));
    EngineMappingPermissionGuard guard = new EngineMappingPermissionGuard(userRoleProvider, "ADMIN");

    assertThatThrownBy(() -> guard.requirePermission(EngineMappingPermissions.MAPPING_VIEW))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("RBAC 查询失败")
        .hasMessageContaining(EngineMappingPermissions.MAPPING_VIEW);
  }

  private static Role role(String id, List<OuroborosGrantedAuthority> authorities) {
    return new Role() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public String getName() {
        return id;
      }

      @Override
      public List<? extends OuroborosGrantedAuthority> getAuthorities() {
        return authorities;
      }

      @Override
      public boolean isBuiltinRole() {
        return false;
      }
    };
  }

  private static OuroborosGrantedAuthority authority(String value) {
    OuroborosGrantedAuthority authority = mock(OuroborosGrantedAuthority.class);
    when(authority.getAuthority()).thenReturn(value);
    return authority;
  }
}
