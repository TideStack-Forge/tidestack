package com.ouroboros.ability.componentlib;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class PlatformComponentLibAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(PlatformComponentLibAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Platform ComponentLib Runtime Auto Configuration loaded");
  }
}
