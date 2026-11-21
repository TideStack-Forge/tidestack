package com.ouroboros.dev.componentlib.service;

import com.ouroboros.dev.componentlib.dto.DevAppPlatformComponentLibDTO;
import com.ouroboros.dev.componentlib.dto.ImportedPlatformComponentLibDTO;

/**
 * 应用组件库服务接口，定义了与应用组件库相关的操作方法
 */
public interface DevAppPlatformComponentLibService {
  /**
   * 获取指定应用的开发组件库信息
   *
   * @param appName 应用名称
   * @return DevAppPlatformComponentLibDTO 包含应用组件库信息的数据传输对象
   */
  DevAppPlatformComponentLibDTO getDevAppComponentLib(String appName);

  /**
   * 导入组件库到指定应用
   *
   * @param appName               应用名称
   * @param componentLibImportDTO 组件库导入数据传输对象，包含导入所需的所有信息
   */
  void importComponentLib(String appName, ImportedPlatformComponentLibDTO componentLibImportDTO);

  /**
   * 从指定应用中移除组件库
   *
   * @param appName          应用名称
   * @param componentLibName 要移除的组件库名称
   */
  void removeComponentLib(String appName, String componentLibName);
}
