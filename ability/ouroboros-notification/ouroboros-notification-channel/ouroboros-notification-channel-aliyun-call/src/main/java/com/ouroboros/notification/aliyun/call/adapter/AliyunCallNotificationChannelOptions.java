package com.ouroboros.notification.aliyun.call.adapter;

import com.ouroboros.notification.adapter.NotificationChannelOptions;

/**
 * @author liansz
 */
public class AliyunCallNotificationChannelOptions implements NotificationChannelOptions {

  /**
   * 被叫显号
   */
  private String calledShowNumber;

  /**
   * 一次通知内容播放次数（1-3）默认3
   */
  private Integer playTimes;

  /**
   * 语音通知播放音量（0-100）默认100
   */
  private Integer volume;

  /**
   * 语速控制（-500~500）
   */
  private Integer speed;

  /**
   * 回执id
   */
  private String outId;

  /**
   * 语音通知产品 ID
   */
  private String prodId;

  public String getCalledShowNumber() {
    return calledShowNumber;
  }

  public void setCalledShowNumber(String calledShowNumber) {
    this.calledShowNumber = calledShowNumber;
  }

  public Integer getPlayTimes() {
    return playTimes;
  }

  public void setPlayTimes(Integer playTimes) {
    this.playTimes = playTimes;
  }

  public Integer getVolume() {
    return volume;
  }

  public void setVolume(Integer volume) {
    this.volume = volume;
  }

  public Integer getSpeed() {
    return speed;
  }

  public void setSpeed(Integer speed) {
    this.speed = speed;
  }

  public String getOutId() {
    return outId;
  }

  public void setOutId(String outId) {
    this.outId = outId;
  }

  public String getProdId() {
    return prodId;
  }

  public void setProdId(String prodId) {
    this.prodId = prodId;
  }
}
