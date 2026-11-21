package com.ouroboros.appmodule.runtime;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan
@Configuration
public class AppModuleRuntimeAutoConfiguration {
  final private static Logger logger = LoggerFactory.getLogger(AppModuleRuntimeAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("AppModule Runtime AutoConfiguration loaded");
  }
}
