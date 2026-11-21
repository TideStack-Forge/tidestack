package com.ouroboros.uischema;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

import com.ouroboros.data.annotation.DataModelScan;

@ComponentScan
@DataModelScan
public class DbUiSchemaAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(DbUiSchemaAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros UI Schema In DB Management loaded");
  }
}
