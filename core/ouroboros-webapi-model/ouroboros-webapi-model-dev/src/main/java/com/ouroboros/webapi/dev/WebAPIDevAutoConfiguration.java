package com.ouroboros.webapi.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan
public class WebAPIDevAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(WebAPIDevAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros UI Model Dev Auto Configuration loaded");
  }
}
