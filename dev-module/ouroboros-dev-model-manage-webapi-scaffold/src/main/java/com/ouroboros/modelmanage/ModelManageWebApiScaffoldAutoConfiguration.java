package com.ouroboros.modelmanage;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author liansz
 */
@Configuration
@ComponentScan
@ServletComponentScan
public class ModelManageWebApiScaffoldAutoConfiguration {

  private final static Logger logger = LoggerFactory.getLogger(ModelManageWebApiScaffoldAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Model Manage Webapi Framework Auto Configuration loaded");
  }
}
