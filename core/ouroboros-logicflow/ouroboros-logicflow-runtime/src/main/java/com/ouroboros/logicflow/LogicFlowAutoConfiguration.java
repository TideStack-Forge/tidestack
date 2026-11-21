package com.ouroboros.logicflow;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author liansz
 */
@ComponentScan
public class LogicFlowAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(LogicFlowAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros LogicFlow Auto Configuration loaded");
  }
}
