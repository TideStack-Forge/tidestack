package com.ouroboros.security.rbac;

import java.util.List;
import java.util.Optional;

/**
 * @author liansz
 */
public interface UserProvider {

  /**
   * 按id获取用户
   *
   * @param id
   * @return
   */
  Optional<? extends User> getUser(String id);

  /**
   * 获取全部用户
   *
   * @return
   */
  List<? extends User> getAllUser();

  /** True only when immutable, unique public subjects are available to OIDC. */
  default boolean publicSubjectsReady() { return false; }
}
