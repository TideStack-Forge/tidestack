package com.ouroboros.dev;

/**
 * @author liansz
 */
public interface AppHomeResolver {

  /**
   * 通过appId获取应用的根目录
   *
   * @param appId
   * @return
   */
  String getAppHomeByAppId(String appId);
}
