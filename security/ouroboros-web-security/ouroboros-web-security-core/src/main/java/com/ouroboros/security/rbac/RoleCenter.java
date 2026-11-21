package com.ouroboros.security.rbac;


import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.ouroboros.util.SPI;

/**
 * @author liansz
 */
public interface RoleCenter {

  /**
   * 根据id获取角色
   *
   * @param id 角色ID
   * @return 角色
   */
  Optional<Role> getRole(String id);

  /**
   * 根据id列表获取角色
   *
   * @param ids 角色id列表
   * @return 角色列表
   */
  List<Role> getRoles(Collection<String> ids);

  /**
   * 获取全部角色
   *
   * @return 全部角色
   */
  List<Role> getAllRoles();

  static RoleCenter instance() {
    return SPI.getCachedPrimaryService(RoleCenter.class);
  }
}
