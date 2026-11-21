package com.ouroboros.dev.app.constant;

import java.nio.file.Paths;

import org.apache.commons.lang3.StringUtils;

/**
 * @author liansz
 */
public interface PropertyConstant {

  /**
   * 宿主机公共应用目录
   * 仅用于创建容器时，挂载宿主机目录使用
   */
  String HOST_WORKSPACE_PATH = System.getenv("HOST_WORKSPACE_PATH");

  String HOST_DEV_WORKSPACE_PATH = StringUtils.isNotEmpty(HOST_WORKSPACE_PATH) ? Paths.get(HOST_WORKSPACE_PATH, "dev").toString() : null;

  String HOST_RUN_WORKSPACE_PATH = StringUtils.isNotEmpty(HOST_WORKSPACE_PATH) ? Paths.get(HOST_WORKSPACE_PATH, "run").toString() : null;
}
