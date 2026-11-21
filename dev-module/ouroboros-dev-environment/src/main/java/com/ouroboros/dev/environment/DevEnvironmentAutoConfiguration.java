package com.ouroboros.dev.environment;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class DevEnvironmentAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(DevEnvironmentAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Dev Environmental Auto Configuration loaded");
  }
}
