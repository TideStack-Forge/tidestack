package com.ouroboros.businesslog.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan
public class BusinessLogDevAutoConfiguration {
  @org.springframework.context.annotation.Bean
  public BusinessLogCodeContextProvider businessLogCodeContextProvider() { return new BusinessLogCodeContextProvider(); }

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @PostConstruct
  void afterPropertiesSet() {
    logger.info("Ouroboros Business Log Dev Auto Configuration loaded");
  }
}
