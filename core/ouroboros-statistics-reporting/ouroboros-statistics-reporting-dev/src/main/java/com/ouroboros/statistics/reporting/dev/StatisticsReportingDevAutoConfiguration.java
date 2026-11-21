package com.ouroboros.statistics.reporting.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.data.annotation.DataModelScan;

@DataModelScan
@Configuration
@ComponentScan
public class StatisticsReportingDevAutoConfiguration {
  private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsReportingDevAutoConfiguration.class);

  @PostConstruct
  void init() {
    LOGGER.info("Ouroboros Statistics Reporting Dev Auto Configuration loaded");
  }
}
