package com.ouroboros.scheduler.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@Configuration
@ComponentScan
public class OuroborosSchedulerDevAutoConfiguration {
  private static Logger logger = LoggerFactory.getLogger(OuroborosSchedulerDevAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Schedule Develop Module loaded");
  }
}
