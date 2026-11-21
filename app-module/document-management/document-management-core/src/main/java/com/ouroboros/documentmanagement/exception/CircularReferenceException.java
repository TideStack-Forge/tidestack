package com.ouroboros.documentmanagement.exception;

import com.ouroboros.exception.BusinessException;

/**
 * 循环引用异常
 *
 * <p>当操作会导致循环引用时抛出此异常。
 *
 * <p>使用场景：
 * <ul>
 *   <li>移动目录时，目标目录是源目录的子孙目录</li>
 *   <li>设置父目录时，会形成循环依赖</li>
 * </ul>
 *
 * <p>示例：
 * <pre>
 * 目录结构：A -> B -> C
 * 尝试将 A 移动到 C 下，会形成循环：A -> B -> C -> A
 * </pre>
 */
public class CircularReferenceException extends BusinessException {

  public static final int ERROR_CODE = -4005;

  private final String sourceId;
  private final String targetId;

  /**
   * 创建循环引用异常
   *
   * @param sourceId 源节点 ID
   * @param targetId 目标节点 ID
   */
  public CircularReferenceException(String sourceId, String targetId) {
    super(ERROR_CODE, String.format("循环引用: 不能将节点 %s 移动到其子孙节点 %s 下", sourceId, targetId));
    this.sourceId = sourceId;
    this.targetId = targetId;
  }

  /**
   * 创建循环引用异常（自定义消息）
   *
   * @param message 异常消息
   */
  public CircularReferenceException(String message) {
    super(ERROR_CODE, message);
    this.sourceId = null;
    this.targetId = null;
  }

  /**
   * 创建循环引用异常（带原因）
   *
   * @param message 异常消息
   * @param cause   原因
   */
  public CircularReferenceException(String message, Throwable cause) {
    super(ERROR_CODE, message, cause);
    this.sourceId = null;
    this.targetId = null;
  }

  /**
   * 获取源节点 ID
   */
  public String getSourceId() {
    return sourceId;
  }

  /**
   * 获取目标节点 ID
   */
  public String getTargetId() {
    return targetId;
  }
}
