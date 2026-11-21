package com.ouroboros.security.rbac;

import java.util.Collections;
import java.util.List;

import com.ouroboros.util.SPI;

public interface UserRoleCenter {

  /**
   * 根据用户ID获取角色列表
   *
   * @param userId 用户ID
   * @return 角色列表
   */
  List<Role> getRolesByUser(String userId);

  /**
   * 根据角色ID获取用户列表
   *
   * @param roleId 角色ID
   * @return 用户列表
   */
  List<User> getUsersByRole(String roleId);

  static UserRoleCenter instance() {
    return SPI.getCachedPrimaryService(UserRoleCenter.class);
  }
}
