package com.ouroboros.ability.auth.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementAccessPolicy;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.management.ConfigurationPreparedWrite;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.util.Json;

public final class ExternalAuthConfigurationManagementPolicy implements ConfigurationManagementAccessPolicy {
  private static final Set<String> COMMON = Set.of("publicBaseUrl", "bindingRateLimit", "externalCall", "jwksCache");
  private static final Set<String> PROTOCOLS = Set.of("ldap", "oauth2", "cas");
  private static final Set<String> SECRETS = Set.of("clientSecret", "searchBindPassword");

  @Override
  public boolean supports(String key) { return UnifiedExternalAuthConfiguration.KEY.equals(key); }

  @Override
  public Optional<ConfigurationSnapshot> projectRead(ConfigurationManagementContext context, ConfigurationSnapshot snapshot) {
    if (!context.authenticated() || context.purpose() != ConfigurationManagementContext.Purpose.MANAGEMENT) { return Optional.empty(); }
    Map<String, Object> projected = new LinkedHashMap<>();
    var current = object(UnifiedExternalAuthConfiguration.toJson(UnifiedExternalAuthConfiguration.parse(snapshot.value())));
    if (ExternalAuthManagementAccess.permits(ExternalAuthManagementAccess.CONFIGURE)) {
      for (String protocol : PROTOCOLS) { projected.put(protocol, rows(current.getOrDefault(protocol, List.of())).stream().map(row -> {
        var safe = new LinkedHashMap<>(row);
        SECRETS.forEach(safe::remove);
        return safe;
      }).toList()); }
    }
    if (ExternalAuthManagementAccess.permits(ExternalAuthManagementAccess.SETTINGS)) {
      COMMON.forEach(key -> { if (current.containsKey(key)) { projected.put(key, current.get(key)); } });
    }
    if (projected.isEmpty()) { return Optional.empty(); }
    snapshot.metadata().setDefaultValue(null);
    return Optional.of(new ConfigurationSnapshot(snapshot.key(), projected, null, snapshot.revision(), snapshot.metadata()));
  }

  @Override
  public Set<String> writeDependencies(String key, ConfigurationMutation mutation) { return Set.of(); }

  @Override
  public ConfigurationPreparedWrite prepareWrite(ConfigurationManagementContext context, ConfigurationSnapshot old,
      ConfigurationMutation mutation, Map<String, ConfigurationSnapshot> dependencies) {
    if (!context.authenticated() || context.purpose() != ConfigurationManagementContext.Purpose.MANAGEMENT
        || !(mutation instanceof ExternalAuthConfigurationMutation command) || command.changes() == null) {
      throw new ValidationException("请通过集成管理修改外部登录配置");
    }
    ExternalAuthManagementAccess.require(ExternalAuthManagementAccess.authority(command.group()));
    var result = new LinkedHashMap<>(object(old.value()));
    if (command.group().equals("settings")) {
      if (!COMMON.containsAll(command.changes().keySet())) { throw invalid(); }
      mergeSettings(result, command.changes());
    } else {
      if (!command.changes().keySet().equals(Set.of("providers"))) { throw invalid(); }
      var before = rows(result.getOrDefault(command.group(), List.of()));
      var replacement = new ArrayList<Map<String, Object>>();
      for (var input : rows(command.changes().get("providers"))) {
        String key = input.get("key") instanceof String text ? text : "";
        if (key.isBlank()) { throw invalid(); }
        var previous = before.stream().filter(row -> key.equals(row.get("key"))).findFirst().orElse(Map.of());
        var merged = new LinkedHashMap<>(previous);
        input.forEach((field, value) -> {
          if (!SECRETS.contains(field) || value instanceof String text && !text.isBlank()) { merged.put(field, value); }
        });
        for (String secret : SECRETS) {
          if (input.get(secret + "Ref") instanceof String reference && !reference.isBlank()) { merged.remove(secret); }
          else if (input.get(secret) instanceof String text && !text.isBlank()) { merged.remove(secret + "Ref"); }
        }
        replacement.add(merged);
      }
      result.put(command.group(), replacement);
    }
    new ExternalAuthConfigurationValidator().validate(result);
    return new ConfigurationPreparedWrite(result, context.userIdentity());
  }

  static Map<String, Object> object(Object value) {
    if (value == null) { throw invalid(); }
    return Json.toMap(value instanceof String text ? text : Json.toJsonString(value));
  }

  private static void mergeSettings(Map<String, Object> target, Map<String, Object> changes) {
    changes.forEach((key, value) -> {
      if (value instanceof Map<?, ?> && target.get(key) instanceof Map<?, ?>) {
        var nested = new LinkedHashMap<>(object(target.get(key)));
        mergeSettings(nested, object(value));
        target.put(key, nested);
      } else { target.put(key, value); }
    });
  }

  private static List<Map<String, Object>> rows(Object value) {
    if (!(value instanceof List<?> values)) { throw invalid(); }
    return values.stream().map(ExternalAuthConfigurationManagementPolicy::object).toList();
  }

  private static ValidationException invalid() { return new ValidationException("外部登录配置变更无效"); }
}
