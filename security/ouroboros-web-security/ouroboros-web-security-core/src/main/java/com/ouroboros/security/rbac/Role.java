package com.ouroboros.security.rbac;

import java.util.List;

import com.ouroboros.security.authority.OuroborosGrantedAuthority;

/**
 * @author liansz
 */
public interface Role {

  /**
   * 主键
   *
   * @return
   */
  String getId();

  /**
   * 名称
   *
   * @return
   */
  String getName();

  /**
   * 权限列表
   *
   * @return
   */
  List<? extends OuroborosGrantedAuthority> getAuthorities();

  /**
   * 是否为应用文件内置角色
   *
   * @return
   */
  default boolean isBuiltinRole() {
    return false;
  }

  /**
   * 是否允许编辑权限项
   *
   * @return
   */
  default boolean canEditAuthorities() {
    return true;
  }

  /**
   * 是否允许删除角色
   *
   * @return
   */
  default boolean canDelete() {
    return true;
  }

  /**
   * 是否允许修改名称
   *
   * @return
   */
  default boolean canRename() {
    return true;
  }

  /**
   * 是否允许管理成员
   *
   * @return
   */
  default boolean canManageUsers() {
    return true;
  }
}
