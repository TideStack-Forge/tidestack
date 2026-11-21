package com.ouroboros.security.rbac;

import java.time.LocalDateTime;

/**
 * 用户-主体绑定关系值对象
 * 
 * @author songmingxu
 */
public interface UserSubject {

  /**
   * 用户标识
   *
   * @return 用户标识
   */
  String getUserIdentity();

  /**
   * 主体类型
   *
   * @return 主体类型（如：EMPLOYEE, CUSTOMER, PARTNER等）
   */
  String getSubjectType();

  /**
   * 主体ID
   *
   * @return 主体ID
   */
  String getSubjectId();
}
