package com.ouroboros.usersetting;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

import com.ouroboros.data.annotation.DataModelScan;

@DataModelScan
@ComponentScan
public class UserSettingAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(UserSettingAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros User Setting Auto Configuration loaded");
  }
}
