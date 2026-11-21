package com.ouroboros.dev.componentlib.service;

import java.io.File;
import java.util.List;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.dev.componentlib.dto.ComponentDefineDTO;
import com.ouroboros.dev.componentlib.dto.PlatformComponentLibDTO;
import com.ouroboros.model.AppInfo;

/**
 * 组件库服务接口
 * 提供组件库相关的操作方法，包括获取组件库列表、获取组件定义列表、检查组件包和验证组件库等功能
 */
public interface PlatformComponentLibService {
  /**
   * 根据组件名称获取组件库信息
   *
   * @param name 组件名称，用于查询对应的组件库信息
   * @return 返回一个Optional包装的ComponentLibDTO对象，可能包含组件库信息，也可能为空
   */
  Optional<PlatformComponentLibDTO> get(String name);

  /**
   * 获取所有组件库
   *
   * @return 返回组件库DTO列表
   */
  List<PlatformComponentLibDTO> getAll();

  /**
   * 获取所有组件定义
   *
   * @return 返回组件定义DTO列表
   */
  List<ComponentDefineDTO> getAllComponentDefine();

  /**
   * 检查组件包
   * 从zip中读取组件定义文件 components.manifest.json 文件，反序列化为 PlatformComponentLibDTO
   *
   * @param zipFile 要检查的组件包文件
   * @return 返回检查结果，成功时包含ComponentLibDTO，失败时包含异常信息
   */
  Try<PlatformComponentLibDTO> inspectComponentLibPackage(File zipFile);

  /**
   * 验证组件库
   * 分别检查以下内容：
   * 1. 验证 name 字段和 version 字段，检查是否有重复的组件库名称，若有重名则检查组件库版本号是否大于旧版本
   * 2. 检查 publicPath 是否重复，从部署路径中查找重复的路径，如果存在且该路径不是旧版本的组件库则返回错误
   * 3. 检查组件定义是否存在重复的定义（非同名组件库中检查重复），重复通过 type + name 进行检查，不同 type 可以重名，如果存在则返回错误
   *
   * @param componentLib 要验证的组件库DTO
   * @return 返回验证结果，成功时为空，失败时包含异常信息
   */
  Try<Void> checkComponentLib(PlatformComponentLibDTO componentLib);

  /**
   * 获取与组件库相关的应用列表
   *
   * @return 返回包含相关应用信息的AppInfo列表
   */
  List<AppInfo> getRelatedApps(PlatformComponentLibDTO componentLib);

  /**
   * 安装指定ZIP包的方法
   *
   * @param zipFile 要安装的ZIP格式文件
   * @return Try<Void> 表示操作结果，成功则包含空值，失败则包含异常信息
   */
  Try<Void> installComponentLibPackage(File zipFile);

  /**
   * 卸载指定组件库的方法
   *
   * @param componentLib 要卸载的组件库DTO
   * @return Try<Void> 表示操作结果，成功则包含空值，失败则包含异常信息
   */
  Try<Void> uninstallComponentLib(PlatformComponentLibDTO componentLib);
}
