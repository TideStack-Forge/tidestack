package com.ouroboros.logicflow.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * @author liansz
 */
@ComponentScan
@Import(LogicFlowDebugStoreConfiguration.class)
public class LogicFlowDevAutoConfiguration {
  @org.springframework.context.annotation.Bean
  public com.ouroboros.logicflow.dev.scriptintelligence.LogicFlowInvocationInputProvider logicFlowInvocationInputProvider() {
    return new com.ouroboros.logicflow.dev.scriptintelligence.LogicFlowInvocationInputProvider();
  }

  private final static Logger logger = LoggerFactory.getLogger(LogicFlowDevAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros LogicFlow Dev Auto Configuration loaded");
  }

  @Bean
  public com.ouroboros.dev.codeintelligence.CodeContextProvider logicFlowCodeContextProvider() {
    return new com.ouroboros.logicflow.dev.scriptintelligence.LogicFlowCodeContextProvider();
  }

}
