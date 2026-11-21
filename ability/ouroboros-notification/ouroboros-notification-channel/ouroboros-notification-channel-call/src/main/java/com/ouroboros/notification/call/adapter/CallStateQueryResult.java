package com.ouroboros.notification.call.adapter;

import java.time.LocalDateTime;

import com.ouroboros.notification.call.enums.CallState;

/**
 * @author liansz
 */
public class CallStateQueryResult {

  /**
   * 呼叫状态
   */
  private CallState state;

  /**
   * 开始时间
   */
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  private LocalDateTime endTime;

  /**
   * 持续时间
   */
  private Long duration;

  public CallState getState() {
    return state;
  }

  public void setState(CallState state) {
    this.state = state;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalDateTime startTime) {
    this.startTime = startTime;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalDateTime endTime) {
    this.endTime = endTime;
  }

  public Long getDuration() {
    return duration;
  }

  public void setDuration(Long duration) {
    this.duration = duration;
  }
}
