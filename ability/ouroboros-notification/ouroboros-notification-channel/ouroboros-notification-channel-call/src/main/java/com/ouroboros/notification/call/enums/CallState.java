package com.ouroboros.notification.call.enums;

/**
 * @author liansz
 */
public enum CallState {

  /**
   * 未知
   */
  UNKNOWN,

  /**
   * 等待中
   */
  PENDING,

  /**
   * 错误
   */
  FAIL,

  /**
   * 未接通
   */
  NOT_CONNECTED,

  /**
   * 已接通
   */
  CONNECTED;

  /**
   * 是否完成，是否为最终态
   *
   * @return 是否完成
   */
  public boolean isFinished() {
    return this == FAIL || this == NOT_CONNECTED || this == CONNECTED;
  }
}
