package com.ouroboros.businesslog;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.data.annotation.DataModelScan;

@ComponentScan
@DataModelScan
@Configuration
public class BusinessLogRuntimeAutoConfiguration {
  final static Logger logger = LoggerFactory.getLogger(BusinessLogRuntimeAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Business Log Runtime Auto Configuration loaded");
  }
}
