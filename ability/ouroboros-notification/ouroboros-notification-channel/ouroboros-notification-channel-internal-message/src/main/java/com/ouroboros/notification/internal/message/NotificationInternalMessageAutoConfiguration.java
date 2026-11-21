package com.ouroboros.notification.internal.message;

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
public class NotificationInternalMessageAutoConfiguration {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @PostConstruct
  public void init() {
    logger.info("Notification Internal-Messaging Auto Configuration loaded");
  }
}
