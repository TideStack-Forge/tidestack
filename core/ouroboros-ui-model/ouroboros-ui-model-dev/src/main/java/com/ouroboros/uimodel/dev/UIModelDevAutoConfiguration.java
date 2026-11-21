package com.ouroboros.uimodel.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan
@Configuration
public class UIModelDevAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(UIModelDevAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("Ouroboros UI Model Dev Auto Configuration loaded");
  }
}
