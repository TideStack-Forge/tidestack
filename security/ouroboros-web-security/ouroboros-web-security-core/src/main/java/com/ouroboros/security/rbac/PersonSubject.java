package com.ouroboros.security.rbac;

/**
 * 人员主体接口 - 代表真实的人员用户
 *
 * @author songmingxu
 */
public interface PersonSubject extends Subject {

  /**
   * 主体类型固定为 "PERSON"
   */
  @Override
  default String getType() {
    return "PERSON";
  }

  /**
   * 显示名称直接使用getName()
   */
  @Override
  default String getDisplayName() {
    return getName();
  }
}
