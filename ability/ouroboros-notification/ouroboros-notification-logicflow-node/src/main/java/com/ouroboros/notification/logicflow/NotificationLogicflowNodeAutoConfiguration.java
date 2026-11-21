package com.ouroboros.notification.logicflow;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author liansz
 */
@ComponentScan
public class NotificationLogicflowNodeAutoConfiguration {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @PostConstruct
  void afterPropertiesSet() {
    logger.info("Ouroboros Notification LogicFlow Node Auto Configuration loaded");
  }
}
