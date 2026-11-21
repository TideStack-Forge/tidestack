package com.ouroboros.uimodel.microweb;

import java.io.File;
import java.io.InputStream;

/**
 * @author liansz
 */
public interface MicroWebDeployService {

  /**
   * 部署
   *
   * @param publicPath  打包公共路径
   * @param inputStream 包流
   */
  void deploy(String publicPath, InputStream inputStream);

  /**
   * 部署方法，用于将指定源文件部署到指定的公共路径
   *
   * @param publicPath 目标公共路径，表示部署的目标位置
   * @param source     源文件，表示需要部署的文件（压缩文件或者目录）
   */
  void deploy(String publicPath, File source);

  /**
   * 删除
   *
   * @param publicPath
   */
  void remove(String publicPath);
}
