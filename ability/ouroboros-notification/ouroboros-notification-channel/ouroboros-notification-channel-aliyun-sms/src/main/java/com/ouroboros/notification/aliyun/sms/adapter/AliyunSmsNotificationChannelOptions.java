package com.ouroboros.notification.aliyun.sms.adapter;

import com.ouroboros.notification.adapter.NotificationChannelOptions;

/**
 * @author liansz
 */
public class AliyunSmsNotificationChannelOptions implements NotificationChannelOptions {

  /**
   * 短信签名
   */
  private String signName;

  /**
   * 上行短信扩展码
   */
  private String SmsUpExtendCode;

  /**
   * 外部流水扩展字段
   */
  private String OutId;

  public String getSignName() {
    return signName;
  }

  public void setSignName(String signName) {
    this.signName = signName;
  }

  public String getSmsUpExtendCode() {
    return SmsUpExtendCode;
  }

  public void setSmsUpExtendCode(String smsUpExtendCode) {
    SmsUpExtendCode = smsUpExtendCode;
  }

  public String getOutId() {
    return OutId;
  }

  public void setOutId(String outId) {
    OutId = outId;
  }
}
