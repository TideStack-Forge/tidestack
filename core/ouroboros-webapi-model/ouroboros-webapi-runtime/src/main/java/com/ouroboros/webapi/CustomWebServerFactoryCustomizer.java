package com.ouroboros.webapi;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

/**
 * 对WEB服务器进行定制化
 * 1. 禁用context-path参数（由WebApiBasePathSupplier进行接管）
 *
 * @author liansz
 **/
@Component
public class CustomWebServerFactoryCustomizer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {
  @Override
  public void customize(ConfigurableServletWebServerFactory factory) {
    factory.setContextPath("");
  }
}

