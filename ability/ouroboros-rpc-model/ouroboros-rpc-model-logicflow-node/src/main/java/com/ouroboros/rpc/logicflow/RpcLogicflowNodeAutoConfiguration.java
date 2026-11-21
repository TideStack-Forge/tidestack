package com.ouroboros.rpc.logicflow;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author liansz
 */
@ComponentScan
public class RpcLogicflowNodeAutoConfiguration {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @PostConstruct
  void afterPropertiesSet() {
    logger.info("Ouroboros RPC LogicFlow Node Auto Configuration loaded");
  }
}
