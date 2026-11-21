package com.ouroboros.uimodel.lowcode;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class LowcodeUIModelAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(LowcodeUIModelAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("Ouroboros UI Model Lowcode Auto Configuration loaded");
  }
}
