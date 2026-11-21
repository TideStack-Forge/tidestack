package com.ouroboros.container.config;

import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author liansz
 */
@Component
@ConfigurationProperties(prefix = "ouroboros.dev.container.docker-api")
public class DockerConfiguration {

  /**
   * docker host url
   */
  private String host;

  /**
   * docker username
   */
  private String username;

  /**
   * docker password
   */
  private String password;

  public String getHost() {
    return Optional.ofNullable(host).orElse("tcp://127.0.0.1:2375");
  }

  public void setHost(String host) {
    this.host = host;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
