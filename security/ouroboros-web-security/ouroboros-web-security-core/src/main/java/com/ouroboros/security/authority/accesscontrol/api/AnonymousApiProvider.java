package com.ouroboros.security.authority.accesscontrol.api;

import java.util.List;

/**
 * @author liansz
 */
public interface AnonymousApiProvider {

  /**
   * 获取匿名访问接口
   *
   * @return 匿名访问接口；示例：GET /api/sys/user/info
   */
  List<String> getAnonymousApis();
}
