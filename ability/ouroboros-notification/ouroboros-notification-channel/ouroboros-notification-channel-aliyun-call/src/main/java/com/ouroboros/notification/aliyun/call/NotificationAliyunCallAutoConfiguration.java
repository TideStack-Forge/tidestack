package com.ouroboros.notification.aliyun.call;

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
public class NotificationAliyunCallAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(NotificationAliyunCallAutoConfiguration.class);

  @Bean
  public NotificationChannelSecretManagementPolicy aliyunCallNotificationSecretManagementPolicy() {
    return new NotificationChannelSecretManagementPolicy("notification.aliyun.call.accessKeySecret");
  }

  @PostConstruct
  public void init() {
    logger.info("Notification Aliyun-Call Auto Configuration loaded");
  }
}
