package com.ouroboros.uischema;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(value = "com.ouroboros.uischema.**")
public class UISchemaAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(UISchemaAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros UI Schema LowCode Runtime Auto Configuration loaded");
  }
}
