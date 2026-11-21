package com.ouroboros.security.rbac;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ouroboros.util.SPI;

/**
 * @author liansz
 */
public interface UserCenter {

  /**
   * 根据id获取用户
   *
   * @param userIdentity
   * @return
   */
  Optional<User> getUser(String userIdentity);

  /**
   * 根据id列表获取用户
   *
   * @param userIdentities
   * @return
   */
  List<User> getUsers(Collection<String> userIdentities);

  /**
   * 获取全部用户
   *
   * @return
   */
  List<User> getAllUsers();

  static UserCenter instance() {
    return SPI.getCachedPrimaryService(UserCenter.class);
  }
  /** All provider matches, for operations that must reject ambiguous account ownership. */
  default List<User> getUserCandidates(String identity) {
    return getUser(identity).map(List::of).orElseGet(List::of);
  }

  /** True only when immutable, unique public subjects are available to OIDC. */
  default boolean publicSubjectsReady() { return false; }
}
