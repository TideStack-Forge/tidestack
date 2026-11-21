package com.ouroboros.dev.datasource;

import java.util.Arrays;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.security.authority.accesscontrol.api.AnyAuthenticationApiProvider;

@DataModelScan
@Configuration
@ComponentScan
public class DevDatasourceAutoConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(DevDatasourceAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Dev Datasource Auto Configuration loaded");
  }

  @Bean
  public AnyAuthenticationApiProvider anyAuthenticationApiProvider() {
    return () -> Arrays.asList("* /api/platform-datasource/**", "* /api/platform-datasource");
  }
}
