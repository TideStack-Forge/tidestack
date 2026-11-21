package com.ouroboros.security.rbac.service;

/**
 * @author liansz
 */
public interface DefaultPasswordGenerator {

  /**
   * 构建默认密码
   *
   * @return
   */
  String generatorDefaultPassword();
}
