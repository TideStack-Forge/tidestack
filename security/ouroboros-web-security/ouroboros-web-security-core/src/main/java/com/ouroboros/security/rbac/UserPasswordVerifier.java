package com.ouroboros.security.rbac;

/**
 * @author liansz
 */
public interface UserPasswordVerifier {

  /**
   * 验证用户密码
   *
   * @param userIdentity 用户唯一标识
   * @param password     密码
   * @return true:验证通过、false:不通过
   */
  boolean verify(String userIdentity, String password);

  /**
   * 验证用户密码
   *
   * @param principal     用户标识
   * @param password      密码
   * @param principalType 用户标识类型
   * @return true:验证通过、false:不通过
   */
  boolean verify(String principal, String password, String principalType);
}
