package com.ouroboros.notification;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

import com.ouroboros.data.annotation.DataModelScan;

/**
 * @author liansz
 */
@DataModelScan
@ComponentScan
public class NotificationManagerAutoConfiguration {
  @org.springframework.context.annotation.Bean
  public com.ouroboros.dev.codeintelligence.CodeContextProvider notificationCodeContextProvider() { return new NotificationCodeContextProvider(); }


  private final Logger logger = LoggerFactory.getLogger(getClass());

  @PostConstruct
  void afterPropertiesSet() {
    logger.info("Ouroboros Notification Manager Auto Configuration loaded");
  }
}
