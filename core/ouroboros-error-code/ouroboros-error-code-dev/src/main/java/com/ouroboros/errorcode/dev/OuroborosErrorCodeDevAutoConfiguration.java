package com.ouroboros.errorcode.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@Configuration
@ComponentScan
public class OuroborosErrorCodeDevAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(OuroborosErrorCodeDevAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Error Code Develop Module loaded");
  }
}
