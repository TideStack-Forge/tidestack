package com.ouroboros.security.config;

import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;

@Component
public class SessionConfig {
  ConfigurationService configurationService;

  public SessionConfig(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public Boolean isMaxSessionControl() {
    return configurationService.get(Boolean.class, "security.session.maxSessionsControl")
        .orElse(false);
  }

  public Integer getMaxSessionsPerUser() {
    return configurationService.get(Integer.class, "security.session.maxSessionsPerUser")
        .orElse(-1);
  }

  public Integer getMaxSessionsPerUserClient() {
    return configurationService.get(Integer.class, "security.session.maxSessionsPerUserClient")
        .orElse(-1);
  }

  public Boolean isMaxSessionsPreventsLogin() {
    return configurationService.get(Boolean.class, "security.session.maxSessionsPreventsLogin")
        .orElse(false);
  }
}
