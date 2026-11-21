package com.ouroboros.mq.dev;


import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@Configuration
@ComponentScan
public class OuroborosMQDevelopAutoConfiguration {
  static final Logger logger = LoggerFactory.getLogger(OuroborosMQDevelopAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("Ouroboros MQ Develop Module loaded");
  }
}
