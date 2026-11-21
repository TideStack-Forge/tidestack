package com.ouroboros.ability.auth.oidc.registration;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ouroboros.ability.auth.oidc.repository.ConfigurationRegisteredClientRepository;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.util.Json;

/** Server-level registration policy, owned by the existing configuration mechanism. */
public record DcrSettings(boolean enabled, Mode mode, List<String> allowedClientTypes,
    List<String> allowedIdentityScopes, List<String> allowedGrantTypes, RedirectPolicy redirectPolicy,
    int maxDynamicClients, RateLimit rateLimit, Duration initialAccessTokenTimeToLive) {
  public static final String KEY = "security.auth.oauth2.registration";
  public enum Mode { INITIAL_ACCESS_TOKEN, OPEN }
  public record RedirectPolicy(List<String> allowedHttpsOrigins, boolean allowLoopbackHttp) {
    public RedirectPolicy {
      allowedHttpsOrigins = List.copyOf(allowedHttpsOrigins);
      for (String value : allowedHttpsOrigins) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
            || uri.getFragment() != null || uri.getQuery() != null || !uri.getRawPath().isEmpty()
            || value.contains("*")) { throw invalid(); }
      }
    }
  }
  public record RateLimit(Duration window, int global, int perSource) {
    public RateLimit {
      if (window == null || window.compareTo(Duration.ofSeconds(1)) < 0 || window.compareTo(Duration.ofDays(1)) > 0
          || global < 1 || global > 1_000_000 || perSource < 1 || perSource > global) { throw invalid(); }
    }
  }
  public DcrSettings {
    allowedClientTypes = List.copyOf(allowedClientTypes);
    allowedIdentityScopes = List.copyOf(allowedIdentityScopes);
    allowedGrantTypes = List.copyOf(allowedGrantTypes);
    if (mode == null || allowedClientTypes.isEmpty() || !Set.of("PUBLIC", "CONFIDENTIAL").containsAll(allowedClientTypes)
        || !ConfigurationRegisteredClientRepository.IDENTITY_SCOPES.containsAll(allowedIdentityScopes)
        || !allowedIdentityScopes.contains("openid") || !allowedGrantTypes.contains("authorization_code")
        || !Set.of("authorization_code", "refresh_token").containsAll(allowedGrantTypes)
        || redirectPolicy == null || rateLimit == null || maxDynamicClients < 1 || maxDynamicClients > 100_000
        || initialAccessTokenTimeToLive == null || initialAccessTokenTimeToLive.compareTo(Duration.ofMinutes(1)) < 0
        || initialAccessTokenTimeToLive.compareTo(Duration.ofDays(1)) > 0
        || allowedIdentityScopes.contains("offline_access") && (!allowedClientTypes.contains("CONFIDENTIAL")
            || !allowedGrantTypes.contains("refresh_token"))) { throw invalid(); }
  }
  public static DcrSettings defaults() {
    return new DcrSettings(false, Mode.INITIAL_ACCESS_TOKEN, List.of("PUBLIC", "CONFIDENTIAL"), List.of("openid", "profile"),
        List.of("authorization_code"), new RedirectPolicy(List.of(), true), 100,
        new RateLimit(Duration.ofMinutes(1), 30, 5), Duration.ofMinutes(30));
  }
  @com.fasterxml.jackson.annotation.JsonValue
  public Map<String,Object> toValue() {
    return Map.of("enabled", enabled, "mode", mode.name(), "allowedClientTypes", allowedClientTypes,
        "allowedIdentityScopes", allowedIdentityScopes, "allowedGrantTypes", allowedGrantTypes,
        "redirectPolicy", Map.of("allowedHttpsOrigins", redirectPolicy.allowedHttpsOrigins(), "allowLoopbackHttp", redirectPolicy.allowLoopbackHttp()),
        "maxDynamicClients", maxDynamicClients, "rateLimit", Map.of("window", rateLimit.window().toString(), "global", rateLimit.global(), "perSource", rateLimit.perSource()),
        "initialAccessTokenTimeToLive", initialAccessTokenTimeToLive.toString());
  }
  @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
  public static DcrSettings parse(Object value) {
    if (value instanceof DcrSettings settings) { return settings; }
    try {
      Map<String,Object> values = object(value);
      if (!values.keySet().equals(defaults().toValue().keySet())) { throw invalid(); }
      var redirect = object(values.get("redirectPolicy"));
      var rates = object(values.get("rateLimit"));
      if (!redirect.keySet().equals(Set.of("allowedHttpsOrigins", "allowLoopbackHttp"))
          || !rates.keySet().equals(Set.of("window", "global", "perSource"))) { throw invalid(); }
      return new DcrSettings(bool(values.get("enabled")), Mode.valueOf((String) values.get("mode")),
          strings(values.get("allowedClientTypes")), strings(values.get("allowedIdentityScopes")), strings(values.get("allowedGrantTypes")),
          new RedirectPolicy(strings(redirect.get("allowedHttpsOrigins")), bool(redirect.get("allowLoopbackHttp"))),
          integer(values.get("maxDynamicClients")), new RateLimit(Duration.parse((String) rates.get("window")), integer(rates.get("global")), integer(rates.get("perSource"))),
          Duration.parse((String) values.get("initialAccessTokenTimeToLive")));
    } catch (IllegalArgumentException | ClassCastException | NullPointerException error) { throw invalid(); }
  }
  private static Map<String,Object> object(Object value) {
    return Json.tryToMap(value instanceof String text ? text : Json.toJsonString(value))
        .filter(java.util.Objects::nonNull).getOrElseThrow(error -> invalid());
  }
  private static List<String> strings(Object value) {
    if (!(value instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof String))) { throw invalid(); }
    return list.stream().map(String.class::cast).distinct().toList();
  }
  private static boolean bool(Object value) { if (value instanceof Boolean flag) { return flag; } throw invalid(); }
  private static int integer(Object value) {
    if (value instanceof Number number && number.doubleValue() == number.intValue()) { return number.intValue(); }
    throw invalid();
  }
  private static ValidationException invalid() { return new ValidationException("[DCR_SETTINGS_INVALID] 动态注册策略无效"); }
}
