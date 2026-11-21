package com.ouroboros.dev.componentlib;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class DevComponentLibAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(DevComponentLibAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Dev Platform ComponentLib Auto Configuration loaded");
  }
}
