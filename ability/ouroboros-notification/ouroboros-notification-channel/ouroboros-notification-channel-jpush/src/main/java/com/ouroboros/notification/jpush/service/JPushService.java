package com.ouroboros.notification.jpush.service;

import java.util.List;

import com.ouroboros.notification.jpush.adapter.JPushNotificationChannelOptions;

/**
 * @author liansz
 */
public interface JPushService {

  /**
   * 发送通知
   *
   * @param registrationIds
   * @param title
   * @param content
   * @param channelOptions
   */
  void sendNotification(List<String> registrationIds,
                        String title, String content, JPushNotificationChannelOptions channelOptions);
}
