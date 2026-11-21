package com.ouroboros.data.dict.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author sunquan
 */
@Configuration
@ComponentScan
public class DataDictDevAutoConfiguration {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Data Dict Dev Auto Configuration loaded");
  }
}
