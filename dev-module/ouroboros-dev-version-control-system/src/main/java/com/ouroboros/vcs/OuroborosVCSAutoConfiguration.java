package com.ouroboros.vcs;

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
public class OuroborosVCSAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(OuroborosVCSAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros VCS Auto Configuration loaded");
  }
}
