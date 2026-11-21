package com.ouroboros.notification.email.service;

import com.ouroboros.notification.email.adapter.EmailNotificationChannelOptions;

/**
 * @author liansz
 */
public interface EmailService {

  /**
   * 发送邮件
   *
   * @param email
   * @param title
   * @param content
   * @param channelOptions
   */
  void sendEmail(String email, String title, String content, EmailNotificationChannelOptions channelOptions);
}
