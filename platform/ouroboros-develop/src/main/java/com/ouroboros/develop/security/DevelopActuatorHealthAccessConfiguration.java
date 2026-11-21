package com.ouroboros.develop.security;

import java.util.Collections;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;

@Configuration
public class DevelopActuatorHealthAccessConfiguration {

  @Bean("developActuatorHealthAnonymousApiProvider")
  public AnonymousApiProvider developActuatorHealthAnonymousApiProvider() {
    return () -> Collections.singletonList("GET /actuator/health");
  }
}
