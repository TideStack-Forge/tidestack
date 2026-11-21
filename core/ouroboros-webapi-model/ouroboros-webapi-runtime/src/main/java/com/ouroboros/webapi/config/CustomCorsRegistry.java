package com.ouroboros.webapi.config;

import java.util.List;
import java.util.Map;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class CustomCorsRegistry extends CorsRegistry {
  public static Map<String, CorsConfiguration> getCorsConfigurations(List<WebMvcConfigurer> webMvcConfigurers) {
    var registry = new CustomCorsRegistry();
    webMvcConfigurers.forEach(c -> c.addCorsMappings(registry));
    return registry.getCorsConfigurations();
  }
}