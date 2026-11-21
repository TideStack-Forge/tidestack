package com.ouroboros.uischema.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan
@Configuration
public class UISchemaDevAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(UISchemaDevAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("Ouroboros UI Model UI Schema Dev Auto Configuration loaded");
  }
}
