package com.ouroboros.logicflow.debug;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@Configuration
@ComponentScan(basePackageClasses = LogicFlowDebugRuntimeSupport.class)
public class LogicFlowDebugHelperAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(LogicFlowDebugHelperAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("Ouroboros LogicFlow Debug Helper Auto Configuration loaded");
  }
}
