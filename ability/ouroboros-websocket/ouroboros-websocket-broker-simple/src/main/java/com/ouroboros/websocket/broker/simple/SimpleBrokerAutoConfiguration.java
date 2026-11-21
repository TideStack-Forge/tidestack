package com.ouroboros.websocket.broker.simple;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.websocket.WebSocketBrokerStrategy;

@Configuration
@ConditionalOnProperty(name = "ouroboros.websocket.broker-type", havingValue = "simple", matchIfMissing = true)
public class SimpleBrokerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(WebSocketBrokerStrategy.class)
  public WebSocketBrokerStrategy simpleBrokerStrategy() {
    return new SimpleBrokerStrategy();
  }
}
