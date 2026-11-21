package com.ouroboros.errorcode.runtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.errorcode.registry.ErrorCodeRegistry;
import com.ouroboros.errorcode.runtime.registry.DefaultErrorCodeRegistry;

@Configuration
public class OuroborosErrorCodeRuntimeAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(ErrorCodeRegistry.class)
  public ErrorCodeRegistry errorCodeRegistry() {
    return new DefaultErrorCodeRegistry();
  }
}
