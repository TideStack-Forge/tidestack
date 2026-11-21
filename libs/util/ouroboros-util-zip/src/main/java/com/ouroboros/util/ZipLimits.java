package com.ouroboros.util;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZipLimits类提供ZIP操作的安全限制配置，防止资源耗尽攻击和恶意文件处理。
 * 支持多种预定义场景配置和完全自定义配置。
 * 预定义场景：
 * - strict(): Web应用、用户上传等不可信环境
 * - standard(): 企业应用的标准配置
 * - relaxed(): 内部工具、批处理等可信环境
 * - adaptive(): 根据系统资源动态调整
 * - unlimited(): 完全无限制，适用于可信的大数据处理
 */
@SuppressWarnings("unused")
public class ZipLimits {
  private static final Logger logger = LoggerFactory.getLogger(ZipLimits.class);

  // 默认限制值 - 基于常见场景的合理估计
  public static final long DEFAULT_MAX_ENTRY_SIZE = 50L * 1024 * 1024;
  public static final long DEFAULT_MAX_TOTAL_SIZE = 500L * 1024 * 1024;
  public static final int DEFAULT_MAX_ENTRIES = 5000;
  public static final long DEFAULT_MAX_MEMORY_USAGE = 100L * 1024 * 1024;
  public static final long DEFAULT_TIMEOUT_SECONDS = 300;

  private final long maxEntrySize;
  private final long maxTotalSize;
  private final int maxEntries;
  private final long maxMemoryUsage;
  private final long timeoutSeconds;
  private final boolean enableLimits;

  // 私有构造器
  private ZipLimits(long maxEntrySize, long maxTotalSize, int maxEntries,
                    long maxMemoryUsage, long timeoutSeconds, boolean enableLimits) {
    this.maxEntrySize = maxEntrySize;
    this.maxTotalSize = maxTotalSize;
    this.maxEntries = maxEntries;
    this.maxMemoryUsage = maxMemoryUsage;
    this.timeoutSeconds = timeoutSeconds;
    this.enableLimits = enableLimits;
  }

  // ==================== 预定义场景配置 ====================

  /**
   * 无限制模式 - 适用于可信环境或大数据处理
   * 注意：仍然保留基本的安全检查，但取消大小和数量限制
   */
  public static ZipLimits unlimited() {
    return new ZipLimits(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE,
        Long.MAX_VALUE, Long.MAX_VALUE, false);
  }

  /**
   * 严格模式 - 适用于Web应用、用户上传等不可信环境
   * - 10MB单文件限制，防止单个恶意大文件
   * - 50MB总大小限制，控制存储和传输成本
   * - 1000个文件限制，防止文件数攻击
   * - 20MB内存限制，考虑多用户并发
   * - 60秒超时，快速失败
   */
  public static ZipLimits strict() {
    return new ZipLimits(
        10L * 1024 * 1024,
        50L * 1024 * 1024,
        1000,
        20L * 1024 * 1024,
        60,
        true
    );
  }

  /**
   * 标准模式 - 适用于大多数企业应用
   * - 50MB单文件限制，平衡功能性和安全性
   * - 500MB总大小限制，适合常见业务需求
   * - 5000个文件限制，支持中等规模项目
   * - 100MB内存限制，合理的资源使用
   * - 300秒超时，足够处理大部分任务
   */
  public static ZipLimits standard() {
    return new ZipLimits(
        DEFAULT_MAX_ENTRY_SIZE,
        DEFAULT_MAX_TOTAL_SIZE,
        DEFAULT_MAX_ENTRIES,
        DEFAULT_MAX_MEMORY_USAGE,
        DEFAULT_TIMEOUT_SECONDS,
        true
    );
  }

  /**
   * 宽松模式 - 适用于内部工具、批处理等可信环境
   * - 200MB单文件限制，支持大型文档和媒体文件
   * - 2GB总大小限制，适合数据备份和批处理
   * - 20000个文件限制，支持大型项目结构
   * - 500MB内存限制，充分利用服务器资源
   * - 1800秒(30分钟)超时，支持长时间处理
   */
  public static ZipLimits relaxed() {
    return new ZipLimits(
        200L * 1024 * 1024,
        2L * 1024 * 1024 * 1024,
        20000,
        500L * 1024 * 1024,
        1800,
        true
    );
  }

  /**
   * 基于系统资源动态计算的限制
   * 根据当前JVM可用内存和处理器数量智能调整限制
   */
  public static ZipLimits adaptive() {
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory();
    long totalMemory = runtime.totalMemory();
    long freeMemory = runtime.freeMemory();
    long availableMemory = maxMemory - (totalMemory - freeMemory);
    int processors = runtime.availableProcessors();

    // 使用可用内存的1/8作为zip处理的内存限制（保守估计）
    // 最少50MB
    long memoryLimit = Math.max(availableMemory / 8, 50L * 1024 * 1024);

    // 根据可用内存调整其他限制
    // 最大500MB
    long maxEntrySize = Math.min(memoryLimit / 2, 500L * 1024 * 1024);
    // 最大5GB
    long maxTotalSize = Math.min(memoryLimit * 10, 5L * 1024 * 1024 * 1024);

    // 根据处理器数量调整文件数量限制
    // 每核心2000个文件，最大5万
    int maxEntries = Math.min(processors * 2000, 50000);

    // 根据系统规模调整超时
    // 每核心1分钟，最少5分钟
    long timeoutSeconds = Math.max(300, processors * 60);

    logger.info("Adaptive limits calculated: maxEntry={}MB, maxTotal={}MB, maxEntries={}, " +
            "maxMemory={}MB, timeout={}s, availableMemory={}MB, processors={}",
        maxEntrySize / 1024 / 1024, maxTotalSize / 1024 / 1024, maxEntries,
        memoryLimit / 1024 / 1024, timeoutSeconds, availableMemory / 1024 / 1024, processors);

    return new ZipLimits(maxEntrySize, maxTotalSize, maxEntries, memoryLimit, timeoutSeconds, true);
  }

  /**
   * 自定义建造者
   */
  public static ZipLimitsBuilder custom() {
    return new ZipLimitsBuilder();
  }

  // ==================== 验证方法 ====================

  /**
   * 验证单个条目大小
   * @param size 条目大小（字节）
   * @param entryName 条目名称，用于错误信息
   * @throws IOException 如果超出限制
   */
  public void validateEntrySize(long size, String entryName) throws IOException {
    if (!enableLimits || size < 0) {
      return; // 无限制模式或未知大小跳过检查
    }

    if (size > maxEntrySize) {
      String message = String.format(
          "Entry '%s' size (%s) exceeds limit (%s)",
          entryName, formatSize(size), formatSize(maxEntrySize));
      logger.warn(message);
      throw new IOException(message);
    }
  }

  /**
   * 验证总大小限制
   * @param totalSize 当前总大小（字节）
   * @throws IOException 如果超出限制
   */
  public void validateTotalSize(long totalSize) throws IOException {
    if (!enableLimits) {
      return;
    }

    if (totalSize > maxTotalSize) {
      String message = String.format(
          "Total size (%s) exceeds limit (%s)",
          formatSize(totalSize), formatSize(maxTotalSize));
      logger.warn(message);
      throw new IOException(message);
    }
  }

  /**
   * 验证条目数量
   * @param count 当前条目数量
   * @throws IOException 如果超出限制
   */
  public void validateEntryCount(int count) throws IOException {
    if (!enableLimits) {
      return;
    }

    if (count > maxEntries) {
      String message = String.format(
          "Entry count (%d) exceeds limit (%d)", count, maxEntries);
      logger.warn(message);
      throw new IOException(message);
    }
  }

  /**
   * 验证内存使用量
   * @param memoryUsage 当前内存使用量（字节）
   * @throws IOException 如果超出限制
   */
  public void validateMemoryUsage(long memoryUsage) throws IOException {
    if (!enableLimits) {
      return;
    }

    if (memoryUsage > maxMemoryUsage) {
      String message = String.format(
          "Memory usage (%s) exceeds limit (%s)",
          formatSize(memoryUsage), formatSize(maxMemoryUsage));
      logger.warn(message);
      throw new IOException(message);
    }
  }

  /**
   * 验证处理时间
   * @param startTime 开始时间戳（毫秒）
   * @throws IOException 如果超时
   */
  public void validateTimeout(long startTime) throws IOException {
    if (!enableLimits || timeoutSeconds == Long.MAX_VALUE) {
      return;
    }

    long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
    if (elapsedSeconds > timeoutSeconds) {
      String message = String.format(
          "Operation timeout (%d seconds) exceeds limit (%d seconds)",
          elapsedSeconds, timeoutSeconds);
      logger.warn(message);
      throw new IOException(message);
    }
  }

  /**
   * 创建超时检查器，用于长时间运行的操作
   * @return 超时检查器
   */
  public TimeoutChecker createTimeoutChecker() {
    return new TimeoutChecker(System.currentTimeMillis());
  }

  // ==================== 辅助方法 ====================

  /**
   * 检查是否启用了限制
   */
  public boolean isEnabled() {
    return enableLimits;
  }

  /**
   * 检查是否为无限制模式
   */
  public boolean isUnlimited() {
    return !enableLimits;
  }

  /**
   * 获取限制摘要信息
   */
  public String getSummary() {
    if (!enableLimits) {
      return "Unlimited mode - no restrictions";
    }

    return String.format(
        "Limits: maxEntry=%s, maxTotal=%s, maxCount=%d, maxMemory=%s, timeout=%ds",
        formatSize(maxEntrySize), formatSize(maxTotalSize), maxEntries,
        formatSize(maxMemoryUsage), timeoutSeconds
    );
  }

  // Getters
  public long getMaxEntrySize() { return maxEntrySize; }
  public long getMaxTotalSize() { return maxTotalSize; }
  public int getMaxEntries() { return maxEntries; }
  public long getMaxMemoryUsage() { return maxMemoryUsage; }
  public long getTimeoutSeconds() { return timeoutSeconds; }
  public boolean isEnableLimits() { return enableLimits; }

  @Override
  public String toString() {
    return getSummary();
  }

  /**
   * 格式化文件大小为可读字符串
   */
  private static String formatSize(long bytes) {
    if (bytes == Long.MAX_VALUE) {
      return "unlimited";
    }

    if (bytes >= 1024L * 1024 * 1024 * 1024) {
      return String.format("%.1fTB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
    } else if (bytes >= 1024L * 1024 * 1024) {
      return String.format("%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0));
    } else if (bytes >= 1024 * 1024) {
      return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    } else if (bytes >= 1024) {
      return String.format("%.1fKB", bytes / 1024.0);
    } else {
      return bytes + "B";
    }
  }

  // ==================== 内部类 ====================

  /**
   * 超时检查器
   */
  public class TimeoutChecker {
    private final long startTime;

    private TimeoutChecker(long startTime) {
      this.startTime = startTime;
    }

    /**
     * 检查是否超时
     * @throws IOException 如果超时
     */
    public void checkTimeout() throws IOException {
      validateTimeout(startTime);
    }

    /**
     * 获取已经过的时间（秒）
     */
    public long getElapsedSeconds() {
      return (System.currentTimeMillis() - startTime) / 1000;
    }

    /**
     * 获取剩余时间（秒）
     */
    public long getRemainingSeconds() {
      if (!enableLimits || timeoutSeconds == Long.MAX_VALUE) {
        return Long.MAX_VALUE;
      }
      return Math.max(0, timeoutSeconds - getElapsedSeconds());
    }
  }

  /**
   * 建造者类
   */
  public static class ZipLimitsBuilder {
    private long maxEntrySize = DEFAULT_MAX_ENTRY_SIZE;
    private long maxTotalSize = DEFAULT_MAX_TOTAL_SIZE;
    private int maxEntries = DEFAULT_MAX_ENTRIES;
    private long maxMemoryUsage = DEFAULT_MAX_MEMORY_USAGE;
    private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    public ZipLimitsBuilder maxEntrySize(long maxEntrySize) {
      this.maxEntrySize = maxEntrySize;
      return this;
    }

    public ZipLimitsBuilder maxTotalSize(long maxTotalSize) {
      this.maxTotalSize = maxTotalSize;
      return this;
    }

    public ZipLimitsBuilder maxEntries(int maxEntries) {
      this.maxEntries = maxEntries;
      return this;
    }

    public ZipLimitsBuilder maxMemoryUsage(long maxMemoryUsage) {
      this.maxMemoryUsage = maxMemoryUsage;
      return this;
    }

    public ZipLimitsBuilder timeoutSeconds(long timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
      return this;
    }

    // 便利方法 - MB单位
    public ZipLimitsBuilder maxEntrySizeMB(long megabytes) {
      this.maxEntrySize = megabytes * 1024L * 1024L;
      return this;
    }

    public ZipLimitsBuilder maxTotalSizeMB(long megabytes) {
      this.maxTotalSize = megabytes * 1024L * 1024L;
      return this;
    }

    public ZipLimitsBuilder maxMemoryUsageMB(long megabytes) {
      this.maxMemoryUsage = megabytes * 1024L * 1024L;
      return this;
    }

    // 便利方法 - GB单位
    public ZipLimitsBuilder maxEntrySizeGB(long gigabytes) {
      this.maxEntrySize = gigabytes * 1024L * 1024L * 1024L;
      return this;
    }

    public ZipLimitsBuilder maxTotalSizeGB(long gigabytes) {
      this.maxTotalSize = gigabytes * 1024L * 1024L * 1024L;
      return this;
    }

    public ZipLimitsBuilder maxMemoryUsageGB(long gigabytes) {
      this.maxMemoryUsage = gigabytes * 1024L * 1024L * 1024L;
      return this;
    }

    // 便利方法 - 时间单位
    public ZipLimitsBuilder timeoutMinutes(long minutes) {
      this.timeoutSeconds = minutes * 60;
      return this;
    }

    public ZipLimitsBuilder timeoutHours(long hours) {
      this.timeoutSeconds = hours * 3600;
      return this;
    }

    public ZipLimitsBuilder timeout(long time, TimeUnit unit) {
      this.timeoutSeconds = unit.toSeconds(time);
      return this;
    }

    public ZipLimits build() {
      return new ZipLimits(maxEntrySize, maxTotalSize, maxEntries,
          maxMemoryUsage, timeoutSeconds, true);
    }
  }
}