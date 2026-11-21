package com.ouroboros.appmodule.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan
@Configuration
public class AppModuleDevAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(AppModuleDevAutoConfiguration.class);

  void init() {
    logger.info("AppModule Dev AutoConfiguration loaded");
  }
}
