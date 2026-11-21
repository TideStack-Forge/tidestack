package com.ouroboros.webapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author liansz
 **/
@Component
public class EnvironmentWebAPIBasePathSupplier implements WebAPIBasePathSupplier {

  @Value("${ouroboros.webapi.base-path:${server.servlet.context-path:}}")
  private String webApiBasePath;

  @Override
  public String getWebAPIBasePath() {
    return webApiBasePath;
  }
}
