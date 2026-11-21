package com.ouroboros.security.rbac;

import java.util.Optional;

/**
 * @author liansz
 */
public interface UserIdResolver {

  /**
   * 查找用户唯一标识
   *
   * @param type      类型
   * @param principle 账户
   * @return 用户标识
   */
  Optional<String> getUserId(String type, String principle);
}
