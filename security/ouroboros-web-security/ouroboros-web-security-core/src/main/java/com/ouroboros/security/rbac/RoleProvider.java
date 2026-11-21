package com.ouroboros.security.rbac;

import java.util.Map;
import java.util.Optional;

/**
 * @author liansz
 */
public interface RoleProvider {

  /**
   * 按id获取角色
   *
   * @param id
   * @return
   */
  Optional<Role> getRole(String id);

  /**
   * 获取全部角色
   *
   * @return
   */
  Map<String, Role> getAllRole();
}
