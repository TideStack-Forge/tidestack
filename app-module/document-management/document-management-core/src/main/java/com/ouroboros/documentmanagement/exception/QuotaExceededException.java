package com.ouroboros.documentmanagement.exception;

import com.ouroboros.exception.BusinessException;

/**
 * 配额超限异常
 *
 * <p>当文档库的存储空间或文件数量超过限制时抛出此异常。
 *
 * <p>使用场景：
 * <ul>
 *   <li>上传文件时，文档库存储空间已满</li>
 *   <li>上传文件时，文档库文件数量已达上限</li>
 *   <li>批量操作导致配额超限</li>
 * </ul>
 */
public class QuotaExceededException extends BusinessException {

  public static final int ERROR_CODE = -4003;

  private final String libraryId;
  private final QuotaType quotaType;
  private final Long currentValue;
  private final Long limitValue;

  /**
   * 配额类型枚举
   */
  public enum QuotaType {
    /** 存储空间配额 */
    STORAGE_SIZE,
    /** 文件数量配额 */
    FILE_COUNT
  }

  /**
   * 创建配额超限异常
   *
   * @param libraryId    文档库 ID
   * @param quotaType    配额类型
   * @param currentValue 当前值
   * @param limitValue   限制值
   */
  public QuotaExceededException(
      String libraryId, QuotaType quotaType, Long currentValue, Long limitValue) {
    super(
        ERROR_CODE,
        String.format(
            "文档库配额超限: %s (libraryId=%s, current=%d, limit=%d)",
            quotaType, libraryId, currentValue, limitValue));
    this.libraryId = libraryId;
    this.quotaType = quotaType;
    this.currentValue = currentValue;
    this.limitValue = limitValue;
  }

  /**
   * 创建存储空间配额超限异常
   *
   * @param libraryId    文档库 ID
   * @param currentSize  当前使用空间（字节）
   * @param limitSize    存储空间限制（字节）
   */
  public static QuotaExceededException storageSize(
      String libraryId, long currentSize, long limitSize) {
    return new QuotaExceededException(libraryId, QuotaType.STORAGE_SIZE, currentSize, limitSize);
  }

  /**
   * 创建文件数量配额超限异常
   *
   * @param libraryId    文档库 ID
   * @param currentCount 当前文件数量
   * @param limitCount   文件数量限制
   */
  public static QuotaExceededException fileCount(
      String libraryId, long currentCount, long limitCount) {
    return new QuotaExceededException(libraryId, QuotaType.FILE_COUNT, currentCount, limitCount);
  }

  /**
   * 创建配额超限异常（自定义消息）
   *
   * @param message 异常消息
   */
  public QuotaExceededException(String message) {
    super(ERROR_CODE, message);
    this.libraryId = null;
    this.quotaType = null;
    this.currentValue = null;
    this.limitValue = null;
  }

  /**
   * 获取文档库 ID
   */
  public String getLibraryId() {
    return libraryId;
  }

  /**
   * 获取配额类型
   */
  public QuotaType getQuotaType() {
    return quotaType;
  }

  /**
   * 获取当前值
   */
  public Long getCurrentValue() {
    return currentValue;
  }

  /**
   * 获取限制值
   */
  public Long getLimitValue() {
    return limitValue;
  }
}
