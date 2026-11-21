package com.ouroboros.security.authority;

import java.util.List;

/**
 * @author liansz
 */
public interface AuthorityProvider {

  /**
   * 按名称获取授权
   *
   * @return
   */
  List<Authority> getAuthority(String fullName);

  /**
   * 获取全部授权
   *
   * @return
   */
  List<Authority> getAllAuthorities();
}
