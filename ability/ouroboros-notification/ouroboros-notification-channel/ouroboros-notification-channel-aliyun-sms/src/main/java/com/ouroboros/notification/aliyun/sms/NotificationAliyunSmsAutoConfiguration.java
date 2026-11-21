package com.ouroboros.notification.aliyun.sms;

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
public class NotificationAliyunSmsAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(NotificationAliyunSmsAutoConfiguration.class);

  @Bean
  public NotificationChannelSecretManagementPolicy aliyunSmsNotificationSecretManagementPolicy() {
    return new NotificationChannelSecretManagementPolicy("notification.aliyun.sms.accessKeySecret");
  }

  @PostConstruct
  public void init() {
    logger.info("Notification Aliyun-SMS Auto Configuration loaded");
  }
}
