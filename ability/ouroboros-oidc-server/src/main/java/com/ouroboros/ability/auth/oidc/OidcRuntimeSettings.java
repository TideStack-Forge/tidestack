package com.ouroboros.ability.auth.oidc;

import java.time.Duration;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.util.Json;

public record OidcRuntimeSettings(boolean enabled, Duration accessTokenTimeToLive, Duration refreshTokenTimeToLive) {
  public static final String KEY = "security.auth.oauth2.settings";

  public OidcRuntimeSettings {
    if (accessTokenTimeToLive == null || refreshTokenTimeToLive == null
        || accessTokenTimeToLive.compareTo(Duration.ofMinutes(1)) < 0
        || accessTokenTimeToLive.compareTo(Duration.ofHours(1)) > 0
        || refreshTokenTimeToLive.compareTo(accessTokenTimeToLive) < 0
        || refreshTokenTimeToLive.compareTo(Duration.ofDays(30)) > 0) {
      throw new ValidationException("[AUTH_SERVER_SETTINGS_INVALID] 令牌有效期不在允许范围");
    }
  }

  @com.fasterxml.jackson.annotation.JsonValue
  public java.util.Map<String, Object> toValue() {
    return java.util.Map.of("enabled", enabled, "accessTokenTimeToLive", accessTokenTimeToLive.toString(),
        "refreshTokenTimeToLive", refreshTokenTimeToLive.toString());
  }

  @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
  public static OidcRuntimeSettings parse(Object value) {
    if (value instanceof OidcRuntimeSettings settings) { return settings; }
    String json = value instanceof String text ? text : Json.tryToJsonString(value)
        .getOrElseThrow(error -> invalid());
    var values = Json.tryToMap(json).filter(java.util.Objects::nonNull).getOrElseThrow(error -> invalid());
    if (!values.keySet().equals(java.util.Set.of("enabled", "accessTokenTimeToLive", "refreshTokenTimeToLive"))
        || !(values.get("enabled") instanceof Boolean enabled)
        || !(values.get("accessTokenTimeToLive") instanceof String access)
        || !(values.get("refreshTokenTimeToLive") instanceof String refresh)) { throw invalid(); }
    try { return new OidcRuntimeSettings(enabled, Duration.parse(access), Duration.parse(refresh)); }
    catch (java.time.format.DateTimeParseException error) { throw invalid(); }
  }

  private static ValidationException invalid() { return new ValidationException("[AUTH_SERVER_SETTINGS_INVALID] 授权服务器配置无效"); }

  public static OidcRuntimeSettings current(ConfigurationService configuration) {
    return parse(configuration.getSnapshot(KEY)
        .orElseThrow(() -> new ValidationException("[AUTH_SERVER_UNAVAILABLE] 授权服务器配置不可用")).value());
  }
}
