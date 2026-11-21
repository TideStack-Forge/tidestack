package com.ouroboros.cache.management;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class CacheManagementAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(CacheManagementAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Cache Management Auto Configuration loaded");
  }
}
