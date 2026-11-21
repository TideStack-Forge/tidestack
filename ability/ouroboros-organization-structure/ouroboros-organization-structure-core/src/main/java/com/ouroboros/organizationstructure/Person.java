package com.ouroboros.organizationstructure;

import com.ouroboros.security.rbac.PersonSubject;

/**
 * 人员接口 - 继承PersonSubject，代表真实的人
 *
 * @author songmingxu
 */
public interface Person extends PersonSubject {


  /**
   * 人员唯一标识
   *
   * @return 人员ID
   */
  String getId();

  /**
   * 人员姓名
   *
   * @return 姓名
   */
  String getName();

  /**
   * 性别
   *
   * @return 性别
   */
  String getGender();

  /**
   * 手机号
   *
   * @return 手机号
   */
  String getMobile();

  /**
   * 邮箱
   *
   * @return 邮箱
   */
  String getEmail();

  /**
   * 生日
   *
   * @return 生日
   */
  String getBirthday();
}
