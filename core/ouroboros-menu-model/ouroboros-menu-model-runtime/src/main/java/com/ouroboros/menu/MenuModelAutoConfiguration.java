package com.ouroboros.menu;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author liansz
 */
@ComponentScan
public class MenuModelAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(MenuModelAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Menu Model Auto Configuration loaded");
  }
}
