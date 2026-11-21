package com.ouroboros.rpc.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import com.ouroboros.dev.codeintelligence.CodeContextProvider;

/**
 * @author liansz
 */
@ComponentScan("com.ouroboros.rpc.dev")
public class RpcDevAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(RpcDevAutoConfiguration.class);

  @Bean
  public CodeContextProvider rpcCodeContextProvider(java.util.List<com.ouroboros.dev.codeintelligence.InvocationInputProvider> callers) { return new RpcCodeContextProvider(callers); }

  @PostConstruct
  public void init() {
    logger.info("Ouroboros RPC Dev Auto Configuration loaded");
  }
}
