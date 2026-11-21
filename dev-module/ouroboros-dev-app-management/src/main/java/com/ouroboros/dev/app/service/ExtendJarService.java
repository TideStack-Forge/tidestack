package com.ouroboros.dev.app.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import io.vavr.control.Either;

import com.ouroboros.exception.OuroborosException;

/**
 * 扩展Jar服务接口，提供扩展Jar包的增删改查功能
 */
@SuppressWarnings("unused")
public interface ExtendJarService {
  /**
   * 获取指定应用的所有扩展Jar列表
   * @param appName 应用名称
   * @return 扩展Jar列表
   */
  List<ExtendJarVO> listExtendJars(String appName);

  /**
   * 添加扩展Jar包（通过字节数组）
   * @param appName 应用名称
   * @param jarName Jar包名称
   * @param jarContent Jar包内容（字节数组）
   * @return 返回操作结果，成功为Right，失败为Left
   */
  default Either<OuroborosException, Void> addExtendJar(String appName, String jarName, byte[] jarContent) {
    return addExtendJar(appName, jarName, new ByteArrayInputStream(jarContent));
  }

  /**
   * 添加扩展Jar包（通过输入流）
   * @param appName 应用名称
   * @param jarName Jar包名称
   * @param jarContent Jar包内容（输入流）
   * @return 返回操作结果，成功为Right，失败为Left
   */
  Either<OuroborosException, Void> addExtendJar(String appName, String jarName, InputStream jarContent);

  /**
   * 删除扩展Jar包
   * @param appName 应用名称
   * @param jarName Jar包名称
   * @return 返回操作结果，成功为Right，失败为Left
   */
  Either<OuroborosException, Void> deleteExtendJar(String appName, String jarName);

  /**
   * 更新扩展Jar包（通过字节数组）
   * @param appName 应用名称
   * @param jarName Jar包名称
   * @param jarContent Jar包内容（字节数组）
   * @return 返回操作结果，成功为Right，失败为Left
   */
  default Either<OuroborosException, Void> updateExtendJar(String appName, String jarName, byte[] jarContent) {
    return updateExtendJar(appName, jarName, new ByteArrayInputStream(jarContent));
  }

  /**
   * 更新扩展Jar包（通过输入流）
   * @param appName 应用名称
   * @param jarName Jar包名称
   * @param jarContent Jar包内容（输入流）
   * @return 返回操作结果，成功为Right，失败为Left
   */
  Either<OuroborosException, Void> updateExtendJar(String appName, String jarName, InputStream jarContent);

  /**
   * 扩展Jar包的数据传输对象
   */
  class ExtendJarVO {
    private String fileName;    // 文件名
    private String icon;        // 图标
    private LocalDateTime creationTime;      // 创建时间
    private LocalDateTime lastModifiedTime;  // 最后修改时间
    private Long size;          // 文件大小

    /**
     * 获取文件名
     * @return 文件名
     */
    public String getFileName() {
      return fileName;
    }

    /**
     * 设置文件名
     * @param fileName 文件名
     */
    public void setFileName(String fileName) {
      this.fileName = fileName;
    }

    /**
     * 获取图标
     * @return 图标
     */
    public String getIcon() {
      return icon;
    }

    /**
     * 设置图标
     * @param icon 图标
     */
    public void setIcon(String icon) {
      this.icon = icon;
    }

    /**
     * 获取创建时间
     * @return 创建时间
     */
    public LocalDateTime getCreationTime() {
      return creationTime;
    }

    /**
     * 设置创建时间
     * @param creationTime 创建时间
     */
    public void setCreationTime(LocalDateTime creationTime) {
      this.creationTime = creationTime;
    }

    /**
     * 获取最后修改时间
     * @return 最后修改时间
     */
    public LocalDateTime getLastModifiedTime() {
      return lastModifiedTime;
    }

    /**
     * 设置最后修改时间
     * @param lastModifiedTime 最后修改时间
     */
    public void setLastModifiedTime(LocalDateTime lastModifiedTime) {
      this.lastModifiedTime = lastModifiedTime;
    }

    /**
     * 获取文件大小
     * @return 文件大小
     */
    public Long getSize() {
      return size;
    }

    /**
     * 设置文件大小
     * @param size 文件大小
     */
    public void setSize(Long size) {
      this.size = size;
    }
  }
}
