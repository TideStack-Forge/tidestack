package com.ouroboros.dev.app.service;

import java.util.List;
import java.util.Map;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.AppContainersDTO;
import com.ouroboros.dev.app.model.BaseImagePullRequest;
import com.ouroboros.dev.app.model.BaseImageReference;

/**
 * @author liansz
 */
public interface AppContainerService {

  /**
   * 生成端口号
   *
   * @return 生成包含 web、api 的端口号
   */
  Try<Map<String, Integer>> generateAvailablePortMap();

  /**
   * 获取所有镜像
   *
   * @return 所有基座镜像
   */
  Try<List<String>> getImages();

  /**
   * 获取所有已拉取的基座镜像
   */
  Try<List<BaseImageReference>> getBaseImages();

  /**
   * 获取创建容器时的默认镜像
   */
  Try<String> getDefaultImage();

  /**
   * 拉取基座镜像
   */
  Try<Void> pullBaseImage(BaseImagePullRequest request);

  /**
   * 创建容器
   *
   * @param appName         应用名称
   * @param imageName       基座镜像
   * @param portMap         容器端口号
   * @param configFile      容器配置文件
   * @param environmentName 环境名称
   * @param datasource      数据源
   * @param envMap          容器环境变量
   * @param profileName     profile名（目前是一个用户一个 profile）
   */
  Try<String> create(AppContainerRequest request, String profileName);

  /**
   * 创建并启动容器，避免创建后立即启动场景重复构建运行工作空间。
   */
  Try<String> createAndStart(AppContainerRequest request, String profileName);

  /**
   * 删除容器
   *
   * @param containerId 容器id或容器名称
   */
  Try<Void> delete(String containerId);

  /**
   * 启动容器
   *
   * @param containerId 容器id或容器名称
   */
  Try<Void> start(String containerId);

  /**
   * 停止容器
   *
   * @param containerId 容器id或容器名称
   */
  Try<Void> stop(String containerId);

  /**
   * 暂停指定容器的方法
   *
   * @param containerId 容器ID，用于唯一标识一个容器
   * @return 返回一个Try<Void>对象，表示操作可能成功或失败
   */
  Try<Void> pause(String containerId);

  /**
   * 恢复暂停的容器
   *
   * @param containerId 容器ID，用于标识要恢复的容器
   * @return Try<Void> 表示操作结果的Try容器，成功时包含Void，失败时包含异常信息
   */
  Try<Void> unpause(String containerId);

  /**
   * 终止指定ID的容器
   *
   * @param containerId 容器ID，用于唯一标识一个容器
   * @return Try<Void> 表示操作结果，成功时包含Void，失败时包含异常信息
   */
  Try<Void> kill(String containerId);

  /**
   * 重启容器
   *
   * @param containerId 容器id或容器名称
   */
  Try<Void> restart(String containerId);

  /**
   * 强制重启指定容器的方法
   *
   * @param containerId 容器的唯一标识符
   * @return Try<Void> 表示操作结果，成功时包含Void，失败时包含异常信息
   */
  Try<Void> forceRestart(String containerId);

  /**
   * 重新加载运行工作空间
   */
  Try<Void> reloadWorkspace(String containerId);

  /**
   * 获取所有容器
   */
  Try<List<AppContainersDTO>> getAll();

  /**
   * 更新容器镜像
   */
  Try<String> updateImage(String containerId);

  /**
   * 获取运行状态
   */
  Try<Map<String, Object>> getStatus(String containerId);

  /**
   * 获取指定应用 profile 的运行状态
   */
  Try<Map<String, Object>> getStatus(String appName, String profile);
}
