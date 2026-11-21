package com.ouroboros.resource.manage;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.ouroboros.resource.manage")
public class ResourceRuntimeManageAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(ResourceRuntimeManageAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("Ouroboros Resource Runtime Manage Auto Configuration loaded");
  }
}
