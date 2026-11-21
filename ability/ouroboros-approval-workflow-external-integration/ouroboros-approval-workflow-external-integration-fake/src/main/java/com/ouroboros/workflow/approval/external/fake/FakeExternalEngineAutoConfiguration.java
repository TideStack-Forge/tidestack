package com.ouroboros.workflow.approval.external.fake;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * fake 适配器模块自动配置（演示/灰度）。
 */
@Configuration
@ComponentScan
public class FakeExternalEngineAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(FakeExternalEngineAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Fake External Engine Auto Configuration loaded");
  }
}
