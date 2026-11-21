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
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ANNOTATION,
    classes = org.springframework.boot.autoconfigure.AutoConfiguration.class))
public class NotificationAutoConfiguration {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @PostConstruct
  void afterPropertiesSet() {
    logger.info("Ouroboros Notification Auto Configuration loaded");
  }
}
