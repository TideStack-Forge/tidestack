package com.ouroboros.documentmanagement.exception;

import com.ouroboros.exception.BusinessException;

/**
 * 非法操作异常
 *
 * <p>当操作不符合业务规则或前置条件时抛出此异常。
 *
 * <p>使用场景：
 * <ul>
 *   <li>硬删除文档库前必须先软删除</li>
 *   <li>文档库已被锁定，不能进行修改操作</li>
 *   <li>文档库已被禁用，不能访问</li>
 *   <li>根目录不能被删除或移动</li>
 *   <li>文档库已被删除，不能进行其他操作</li>
 * </ul>
 */
public class InvalidOperationException extends BusinessException {

  public static final int ERROR_CODE = -4002;

  private final String operation;
  private final String reason;

  /**
   * 创建非法操作异常
   *
   * @param operation 操作名称
   * @param reason    不允许的原因
   */
  public InvalidOperationException(String operation, String reason) {
    super(ERROR_CODE, String.format("非法操作: %s - %s", operation, reason));
    this.operation = operation;
    this.reason = reason;
  }

  /**
   * 创建非法操作异常（自定义消息）
   *
   * @param message 异常消息
   */
  public InvalidOperationException(String message) {
    super(ERROR_CODE, message);
    this.operation = null;
    this.reason = null;
  }

  /**
   * 创建非法操作异常（带原因）
   *
   * @param message 异常消息
   * @param cause   原因
   */
  public InvalidOperationException(String message, Throwable cause) {
    super(ERROR_CODE, message, cause);
    this.operation = null;
    this.reason = null;
  }

  /**
   * 获取操作名称
   */
  public String getOperation() {
    return operation;
  }

  /**
   * 获取不允许的原因
   */
  public String getReason() {
    return reason;
  }
}
