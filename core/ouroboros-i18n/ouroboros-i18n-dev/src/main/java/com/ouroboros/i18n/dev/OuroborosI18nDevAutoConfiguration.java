package com.ouroboros.i18n.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@Configuration
@ComponentScan
public class OuroborosI18nDevAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(OuroborosI18nDevAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros I18n Develop Module loaded");
  }
}
