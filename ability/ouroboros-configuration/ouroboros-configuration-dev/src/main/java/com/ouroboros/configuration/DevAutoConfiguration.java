package com.ouroboros.configuration;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

import com.ouroboros.data.annotation.DataModelScan;

/**
 * @author liansz
 */
@DataModelScan
@ComponentScan("com.ouroboros.configuration")
public class DevAutoConfiguration {
  @org.springframework.context.annotation.Bean
  public com.ouroboros.dev.codeintelligence.CodeContextProvider configurationCodeContextProvider() { return new ConfigurationCodeContextProvider(); }


  private final static Logger logger = LoggerFactory.getLogger(DevAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Configuration Dev Auto Configuration loaded");
  }
}
