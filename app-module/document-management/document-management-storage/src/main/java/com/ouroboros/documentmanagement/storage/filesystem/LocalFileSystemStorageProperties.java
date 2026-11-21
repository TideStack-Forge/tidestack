package com.ouroboros.documentmanagement.storage.filesystem;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地文件系统存储配置属性
 *
 * <p>配置示例：
 * <pre>
 * ouroboros.document-management.storage.filesystem.base-path=/var/lib/ouroboros/document-storage
 * ouroboros.document-management.storage.filesystem.path-strategy=hash
 * ouroboros.document-management.storage.filesystem.buffer-size=8192
 * </pre>
 */
@ConfigurationProperties(prefix = "ouroboros.document-management.storage.filesystem")
public class LocalFileSystemStorageProperties {

  /**
   * 文件存储根目录
   */
  private String basePath = "/app/deploy/data/document-libraries";

  /**
   * 路径生成策略
   * <ul>
   *   <li>hash: 基于文件哈希值分层存储（默认）</li>
   *   <li>date: 基于日期分层存储</li>
   *   <li>flat: 平铺存储</li>
   * </ul>
   */
  private PathStrategy pathStrategy = PathStrategy.HASH;

  /**
   * IO 缓冲区大小（字节）
   */
  private int bufferSize = 8192;

  /**
   * 是否计算文件校验和（SHA-256）
   */
  private boolean calculateChecksum = true;

  /**
   * 临时文件目录（用于上传过程）
   */
  private String tempPath;

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public PathStrategy getPathStrategy() {
    return pathStrategy;
  }

  public void setPathStrategy(PathStrategy pathStrategy) {
    this.pathStrategy = pathStrategy;
  }

  public int getBufferSize() {
    return bufferSize;
  }

  public void setBufferSize(int bufferSize) {
    this.bufferSize = bufferSize;
  }

  public boolean isCalculateChecksum() {
    return calculateChecksum;
  }

  public void setCalculateChecksum(boolean calculateChecksum) {
    this.calculateChecksum = calculateChecksum;
  }

  public String getTempPath() {
    return tempPath;
  }

  public void setTempPath(String tempPath) {
    this.tempPath = tempPath;
  }

  /**
   * 路径生成策略
   */
  public enum PathStrategy {
    /**
     * 基于哈希值分层存储（两层目录，避免单目录文件过多）
     * 例如：hash = abc123... -> ab/c1/abc123...
     */
    HASH,

    /**
     * 基于日期分层存储
     * 例如：2024/01/15/uuid.ext
     */
    DATE,

    /**
     * 平铺存储（所有文件在同一目录）
     * 例如：uuid.ext
     */
    FLAT
  }
}
