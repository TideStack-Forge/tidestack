package com.ouroboros.workflow.approval.external.runtime.web;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.security.rbac.UserRoleProvider;
import com.ouroboros.workflow.approval.CurrentUserSupplier;

/**
 * 映射管理权限守卫（Phase 3 最小落地，决策日志 D-5）。
 *
 * <p>规则：① 未登录（CurrentUserSupplier 无身份）→ 拒绝；② RBAC 运行时未装配
 * （userRoleProvider 为 null）→ 拒绝；③ 当前用户任一角色为管理员（{@link EngineMappingPermissions#ADMIN_ROLE}）
 * 或角色 ID/名称等于权限点、或角色 authority 包含权限点 → 放行，否则拒绝。
 */
@Component
public class EngineMappingPermissionGuard {

  private final UserRoleProvider userRoleProvider;
  private final String adminRole;

  @Autowired
  public EngineMappingPermissionGuard(
      @Autowired(required = false) UserRoleProvider userRoleProvider,
      @Value("${ouroboros.workflow.approval.external.admin-role:ADMIN}") String adminRole) {
    this.userRoleProvider = userRoleProvider;
    this.adminRole = adminRole;
  }

  public void requirePermission(String permissionPoint) {
    Optional<String> userId = CurrentUserSupplier.getCurrentUserId();
    if (userId.isEmpty() || "anonymousUser".equals(userId.get())) {
      throw new PlatformException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED + "] 无权限：未登录用户，权限点="
              + permissionPoint);
    }
    if (userRoleProvider == null) {
      throw denied("RBAC 未装配，权限点=" + permissionPoint);
    }
    List<Role> roles;
    try {
      roles = userRoleProvider.getRolesByUser(userId.get());
    } catch (RuntimeException ex) {
      throw new PlatformException(
          "[" + EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED + "] 无权限：RBAC 查询失败，权限点="
              + permissionPoint,
          ex);
    }
    if (roles == null || roles.isEmpty()) {
      throw denied("缺少权限点 " + permissionPoint);
    }
    boolean granted = roles.stream().filter(role -> role != null).anyMatch(role ->
        adminRole.equals(role.getId())
            || adminRole.equals(role.getName())
            || permissionPoint.equals(role.getId())
            || permissionPoint.equals(role.getName())
            || (role.getAuthorities() != null && role.getAuthorities().stream()
                .anyMatch(authority -> permissionPoint.equals(authority.getAuthority()))));
    if (!granted) {
      throw denied("缺少权限点 " + permissionPoint);
    }
  }

  private PlatformException denied(String reason) {
    return new PlatformException(
        "[" + EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED + "] 无权限：" + reason);
  }
}
