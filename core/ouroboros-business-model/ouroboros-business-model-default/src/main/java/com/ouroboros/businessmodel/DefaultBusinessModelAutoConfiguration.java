package com.ouroboros.businessmodel;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Default Business Model Auto Configuration
 *
 * @author Claude Code
 */
@Configuration
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION,
    classes = AutoConfiguration.class))
public class DefaultBusinessModelAutoConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(DefaultBusinessModelAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Default Business Model Auto Configuration loaded");
  }
}
