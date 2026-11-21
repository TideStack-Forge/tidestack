package com.ouroboros.notification.email.service.impl;

import java.util.Date;
import java.util.Properties;
import java.util.function.BiFunction;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.email.adapter.EmailNotificationChannelOptions;
import com.ouroboros.notification.email.service.EmailService;

/**
 * @author liansz
 */
@Service
public class EmailServiceImpl implements EmailService {

  @Autowired
  ConfigurationService configurationService;

  @Override
  public void sendEmail(String email, String title, String content, EmailNotificationChannelOptions channelOptions) {
    BiFunction<Class<?>, String, Object> getConfiguration = (clazz, key) -> configurationService.get(clazz, key)
        .orElseThrow(() -> new BusinessException("邮件通知配置错误: " + key + " 为空"));

    String protocol = (String) getConfiguration.apply(String.class, "notification.email.protocol");
    String host = (String) getConfiguration.apply(String.class, "notification.email.host");
    Integer port = (Integer) getConfiguration.apply(Integer.class, "notification.email.port");
    String username = (String) getConfiguration.apply(String.class, "notification.email.username");
    String authorizationCode = (String) getConfiguration.apply(String.class, "notification.email.authorizationCode");

    // 获取会话
    Session session = getSession(host, protocol, port, username, authorizationCode);

    try {
      // 构建信息
      Message message = new MimeMessage(session);
      message.setFrom(new InternetAddress(username));
      InternetAddress[] address = {new InternetAddress(email)};
      message.setRecipients(Message.RecipientType.TO, address);
      message.setSubject(title);
      message.setSentDate(new Date());
      message.setContent(content, "text/html;charset=utf-8");

      //发送信息
      Transport.send(message);
    } catch (Exception e) {
      throw new BusinessException("邮件发送失败", e);
    }
  }

  /**
   * 获取Session
   *
   * @return session
   */
  private Session getSession(String host, String protocol, int port, String username, String authorizationCode) {
    Properties props = new Properties();
    props.put("mail.smtp.host", host);
    props.put("mail.store.protocol", protocol);
    props.put("mail.smtp.port", port);
    props.put("mail.smtp.auth", "true");

    Authenticator authenticator = new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(username, authorizationCode);
      }
    };

    return Session.getInstance(props, authenticator);
  }
}
