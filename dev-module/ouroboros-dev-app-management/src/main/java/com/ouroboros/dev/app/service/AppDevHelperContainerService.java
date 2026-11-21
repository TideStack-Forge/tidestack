package com.ouroboros.dev.app.service;

import java.util.Map;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppDevHelperRequest;

/**
 * @author liansz
 **/
public interface AppDevHelperContainerService {

  /**
   * 创建
   *
   * @param appName
   * @return
   */
  Try<String> create(AppDevHelperRequest request);

  /**
   * 创建并启动，避免创建后立即启动场景重复同步运行工作空间。
   */
  Try<String> createAndStart(AppDevHelperRequest request);

  /**
   * 删除
   *
   * @param appName
   * @return
   */
  Try<Void> delete(String appName);

  /**
   * 启动
   *
   * @param appName
   * @return
   */
  Try<Void> start(String appName);

  /**
   * 停止
   *
   * @param appName
   * @return
   */
  Try<Void> stop(String appName);

  /**
   * 停止
   *
   * @param appName
   * @return
   */
  Try<Void> kill(String appName);

  /**
   * 重启
   *
   * @param appName
   * @return
   */
  Try<Void> restart(String appName);

  /**
   * 刷新开发助手工作区
   *
   * @param appName
   * @return
   */
  Try<Void> reload(String appName);

  /**
   * 获取运行状态
   *
   * @return 状态信息
   */
  Try<Map<String, Object>> getStatus(String appName);

  /**
   * 同步运行时日志到开发助手日志缓存
   */
  Try<Void> syncRuntimeLogs(String appName);

  /**
   * 更新镜像并重新拉起助手
   */
  Try<String> updateImage(String appName);
}
