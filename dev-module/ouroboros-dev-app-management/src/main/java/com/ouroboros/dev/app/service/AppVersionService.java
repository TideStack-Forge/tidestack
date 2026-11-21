package com.ouroboros.dev.app.service;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppVersionDTO;
import com.ouroboros.vcs.file.VCSFile;

/**
 * AppVersionService 接口，用于管理应用程序版本相关的操作
 * 该接口提供了获取应用版本信息、创建版本以及获取版本文件等功能
 */
public interface AppVersionService {

  /**
   * 获取指定应用的所有版本信息
   * @param appName 应用名称
   * @return 返回包含所有应用版本的 AppVersionDTO 列表
   */
  List<AppVersionDTO> getAll(String appName);

  /**
   * 获取指定应用的特定版本信息
   * @param appName 应用名称
   * @param version 版本号
   * @return 返回包含指定版本信息的 Optional<AppVersionDTO>，如果不存在则为空
   */
  Optional<AppVersionDTO> getOne(String appName, String version);

  /**
   * 创建一个新的应用版本
   * @param appName 应用名称
   * @param version 版本请求
   * @return 返回一个 Try 对象，表示操作成功或失败
   */
  Try<Void> create(String appName, AppVersionDTO version);

  /**
   * 获取指定应用的所有版本文件
   * @param appName 应用名称
   * @return 返回包含所有版本文件的 VCSFile 列表
   */
  List<VCSFile> getAllFiles(String appName);

  /**
   * 获取指定应用特定版本的所有文件
   * @param appName 应用名称
   * @param version 版本号
   * @return 返回包含指定版本所有文件的 VCSFile 列表
   */
  List<VCSFile> getAllFiles(String appName, String version);

  /**
   * 导出指定应用的指定版本
   *
   * @param appName 应用名称
   * @param version 应用版本号
   * @return Try<InputStream> 包含版本导出输入流的Try对象，可能成功或失败
   */
  Try<InputStream> exportVersion(String appName, String version);
}
