package com.ouroboros.dev.app.service;

import java.io.InputStream;
import java.util.List;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppInfoWithMeta;
import com.ouroboros.model.AppInfo;

/**
 * 应用管理服务接口，提供应用创建、更新、删除、重命名、列表查询、导入导出等功能
 */
public interface AppManagementService {
  /**
   * 创建新应用
   * @param app 应用信息对象
   * @return Try对象，包含操作结果
   */
  Try<Void> createApp(AppInfo app);

  /**
   * 更新现有应用
   * @param appName 应用名称
   * @param app 更新的应用信息对象
   * @return Try对象，包含操作结果
   */
  Try<Void> updateApp(String appName, AppInfo app);

  /**
   * 删除指定应用
   * @param appName 应用名称
   * @return Try对象，包含操作结果
   */
  Try<Void> deleteApp(String appName);

  /**
   * 重命名应用
   * @param appName 原应用名称
   * @param newName 新应用名称
   * @return Try对象，包含操作结果
   */
  Try<Void> renameApp(String appName, String newName);

  /**
   * 按类别列出应用
   * @param应用类别
   * @return Try对象，包含应用列表
   */
  Try<List<AppInfoWithMeta>> listApps(String category);

  /**
   * 列出所有应用
   * @return Try对象，包含所有应用列表
   */
  Try<List<AppInfoWithMeta>> listApps();

  /**
   * 检查指定应用是否存在
   * @param appName 应用名称
   * @return 是否存在
   */
  boolean appExists(String appName);

  /**
   * 获取指定应用信息
   * @param appName 应用名称
   * @return Try对象，包含应用信息
   */
  Try<AppInfo> getAppInfo(String appName);

  /**
   * 导入应用
   * @param zipInputStream 应用压缩包输入流
   * @return Try对象，包含操作结果
   */
  Try<Void> importApp(InputStream zipInputStream);

  /**
   * 导出应用
   * @param appName 应用名称
   * @param includeUncommitted 是否包含未提交的更改
   * @param includeGitRepo 是否包含Git仓库
   * @return Try对象，包含应用压缩包输入流
   */
  Try<InputStream> exportApp(String appName, boolean includeUncommitted, boolean includeGitRepo);
}
