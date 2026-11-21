package com.ouroboros.businessmodel.dev;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import com.ouroboros.dev.codeintelligence.CodeContextProvider;

@Configuration
@ComponentScan
public class BusinessModelDevAutoConfiguration {
  Logger logger = LoggerFactory.getLogger(BusinessModelDevAutoConfiguration.class);

  @Bean
  public CodeContextProvider businessModelCodeContextProvider(
      com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService service) {
    return new BusinessModelCodeContextProvider(service);
  }

  @PostConstruct
  void init() {
    logger.info("Business Model Dev AutoConfiguration loaded");
  }
}
