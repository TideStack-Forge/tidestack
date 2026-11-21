package com.ouroboros.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author liansz
 */
@Component
@ConfigurationProperties(prefix = "ouroboros.security")
public class SecurityConfig {

  /**
   * 令牌配置
   */
  private TokenConfig token = new TokenConfig();

  public TokenConfig getToken() {
    return token;
  }

  public void setToken(TokenConfig token) {
    this.token = token;
  }
}
