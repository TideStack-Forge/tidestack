package com.ouroboros.uimodel.microweb;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author liansz
 */
@Configuration
@ComponentScan
public class NginxAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(NginxAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros UI Model Microweb Nginx Auto Configuration loaded");
  }
}
