package com.ouroboros.container;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author liansz
 */
@ComponentScan
public class ContainerDockerAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(ContainerDockerAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Container Auto Configuration loaded");
  }
}
