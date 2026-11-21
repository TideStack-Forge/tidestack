package com.ouroboros.uimodel.dev.microweb;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class MicroWebDevAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(MicroWebDevAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("UI Model Microweb Dev AutoConfiguration loaded");
  }
}
