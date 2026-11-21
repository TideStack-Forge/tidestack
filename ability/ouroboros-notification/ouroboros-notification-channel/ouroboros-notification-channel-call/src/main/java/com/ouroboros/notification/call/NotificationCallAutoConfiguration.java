package com.ouroboros.notification.call;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.ouroboros.data.annotation.DataModelScan;

/**
 * @author liansz
 */
@DataModelScan
@EnableScheduling
@Configuration
public class NotificationCallAutoConfiguration {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @PostConstruct
  void afterPropertiesSet() {
    logger.info("Notification Call Auto Configuration loaded");
  }
}
