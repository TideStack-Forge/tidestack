package com.ouroboros.security.rbac;

/**
 * 用户主体接口 - 统一抽象人员、系统、设备等不同类型的用户主体
 *
 * @author songmingxu
 */
public interface Subject {

  /**
   * 主体唯一标识
   *
   * @return 主体ID
   */
  String getId();

  /**
   * 主体名称
   *
   * @return 主体名称
   */
  String getName();

  /**
   * 主体类型
   *
   * @return 主体类型标识
   */
  String getType();

  /**
   * 获取显示名称
   *
   * @return 用于显示的名称
   */
  String getDisplayName();
}
