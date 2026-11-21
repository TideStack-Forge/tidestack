package com.ouroboros.notification.email;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import com.ouroboros.notification.configuration.NotificationChannelSecretManagementPolicy;

/**
 * @author liansz
 */
@ComponentScan
public class NotificationEmailAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(NotificationEmailAutoConfiguration.class);

  @Bean
  public NotificationChannelSecretManagementPolicy emailNotificationSecretManagementPolicy() {
    return new NotificationChannelSecretManagementPolicy("notification.email.authorizationCode");
  }

  @PostConstruct
  public void init() {
    logger.info("Notification Email Auto Configuration loaded");
  }
}
