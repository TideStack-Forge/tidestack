package com.ouroboros.uimodel.microweb.lowcode;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class LowcodeMicroWebAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(LowcodeMicroWebAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("Ouroboros UI Model MicroWeb Lowcode Auto Configuration loaded");
  }
}
