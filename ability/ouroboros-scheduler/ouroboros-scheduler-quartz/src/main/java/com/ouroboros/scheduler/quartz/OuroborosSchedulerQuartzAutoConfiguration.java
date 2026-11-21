package com.ouroboros.scheduler.quartz;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan
@Configuration
@AutoConfiguration
public class OuroborosSchedulerQuartzAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(OuroborosSchedulerQuartzAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Scheduler Quartz Loaded");
  }
}
