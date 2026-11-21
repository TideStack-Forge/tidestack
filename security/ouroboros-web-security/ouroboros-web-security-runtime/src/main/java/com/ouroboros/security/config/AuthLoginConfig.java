package com.ouroboros.security.config;

import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;

@Component
public class AuthLoginConfig {
  private final ConfigurationService configurationService;

  public AuthLoginConfig(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public boolean isLocalPasswordEnabled() {
    return configurationService.get(Boolean.class, "security.auth.login.localPassword.enabled")
        .orElse(true);
  }
}
