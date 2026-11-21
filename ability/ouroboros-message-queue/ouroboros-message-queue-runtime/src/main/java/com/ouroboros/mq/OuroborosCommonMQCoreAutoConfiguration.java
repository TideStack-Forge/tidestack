package com.ouroboros.mq;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;
import com.ouroboros.mq.binder.EmptyMQBinderReadyChecker;
import com.ouroboros.mq.binding.MQBindingServicePropertiesConfigurer;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration
@Configuration
@Import({StreamMQBridge.class, DefaultProducerCreator.class, EmptyMQBinderReadyChecker.class,
    MQBindingServicePropertiesConfigurer.class})
public class OuroborosCommonMQCoreAutoConfiguration {
  final static Logger logger = LoggerFactory.getLogger(OuroborosCommonMQCoreAutoConfiguration.class);

  @PostConstruct
  void init() {
    logger.info("Ouroboros Common MQ Core Auto Configuration loaded");
  }
}
