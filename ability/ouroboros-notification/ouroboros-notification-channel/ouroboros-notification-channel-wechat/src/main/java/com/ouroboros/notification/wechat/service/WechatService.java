package com.ouroboros.notification.wechat.service;

import java.util.Map;

import com.ouroboros.notification.wechat.adapter.WechatNotificationChannelOptions;

/**
 * @author liansz
 */
public interface WechatService {

  /**
   * 获取微信id
   *
   * @param code
   * @param type openId、unionId
   * @return
   */
  String getWechatId(String code, String type);

  /**
   * 发送模版消息
   *
   * @param wechatId
   * @param templateId
   * @param payload
   * @param channelOptions
   */
  void sendTemplateMessage(String wechatId, String templateId,
                           Map<String, Object> payload, WechatNotificationChannelOptions channelOptions);
}
