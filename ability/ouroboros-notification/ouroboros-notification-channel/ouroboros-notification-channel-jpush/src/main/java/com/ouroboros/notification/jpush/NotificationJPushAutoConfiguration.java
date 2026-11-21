package com.ouroboros.notification.jpush;

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
public class NotificationJPushAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(NotificationJPushAutoConfiguration.class);

  @Bean
  public NotificationChannelSecretManagementPolicy jPushNotificationSecretManagementPolicy() {
    return new NotificationChannelSecretManagementPolicy("notification.jpush.masterSecret");
  }

  @PostConstruct
  public void init() {
    logger.info("Notification J-Push Auto Configuration loaded");
  }
}
