package com.ouroboros.mq.rabbit;


import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@Configuration
@ComponentScan
public class OuroborosRabbitMQAutoConfiguration {
  static final Logger logger = LoggerFactory.getLogger(OuroborosRabbitMQAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("Ouroboros Rabbit MQ Auto Configuration loaded");
  }
}
