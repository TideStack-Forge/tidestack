package com.ouroboros.ability.auth.config;

import com.ouroboros.security.externalauth.config.*;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.exception.PlatformException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Reads the effective external authentication configuration from the configuration service. */
public class UnifiedExternalAuthConfiguration implements ExternalAuthConfiguration {
  private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
      // Editors retain the raw snapshot; a partial installation consumes only its known settings.
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public static String toJson(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new PlatformException("外部认证配置无法序列化", ex);
    }
  }

  public static final String KEY = "security.auth.externalAuth";

  private final ConfigurationService configurationService;

  public UnifiedExternalAuthConfiguration(ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  public ExternalAuthProperties get() {
    Object value = configurationService.getStoredRawValue(KEY)
        .<Object>map(raw -> raw).orElseGet(() -> configurationService.getRequired(KEY));
    return parse(value);
  }

  public static ExternalAuthProperties parse(Object value) {
    String json = value instanceof String text ? text : toJson(value);
    try {
      var properties = JSON.readValue(json, ExternalAuthProperties.class);
      if (properties == null) { throw new ValidationException("外部认证配置不能为空"); }
      return properties;
    } catch (JsonProcessingException ex) {
      throw new PlatformException("外部认证配置无效", ex);
    }
  }

  public String getRevision() {
    return configurationService.getRevision(KEY)
        .orElseThrow(() -> new ValidationException("外部认证配置缺少 revision"));
  }
}
