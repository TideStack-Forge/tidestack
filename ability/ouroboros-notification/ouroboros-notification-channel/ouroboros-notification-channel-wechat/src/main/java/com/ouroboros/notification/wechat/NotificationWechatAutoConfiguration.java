package com.ouroboros.notification.wechat;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import com.ouroboros.notification.configuration.NotificationChannelSecretManagementPolicy;

import com.ouroboros.data.annotation.DataModelScan;

/**
 * @author liansz
 */
@DataModelScan
@ComponentScan
public class NotificationWechatAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(NotificationWechatAutoConfiguration.class);

  @Bean
  public NotificationChannelSecretManagementPolicy wechatNotificationSecretManagementPolicy() {
    return new NotificationChannelSecretManagementPolicy("notification.wechat.secret");
  }

  @PostConstruct
  public void init() {
    logger.info("Notification Wechat Auto Configuration loaded");
  }
}
