package com.ouroboros.notification.aliyun.sms.service;

import java.util.List;
import java.util.Map;

import com.ouroboros.notification.aliyun.sms.adapter.AliyunSmsNotificationChannelOptions;

/**
 * @author liansz
 */
public interface AliyunSmsService {

  /**
   * 发送短信
   *
   * @param mobiles
   * @param templateCode
   * @param payload
   * @param channelOptions
   */
  void sendSms(List<String> mobiles, String templateCode,
               Map<String, Object> payload, AliyunSmsNotificationChannelOptions channelOptions);
}
