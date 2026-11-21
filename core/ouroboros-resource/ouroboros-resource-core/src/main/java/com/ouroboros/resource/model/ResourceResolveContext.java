package com.ouroboros.resource.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resource resolve context.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceResolveContext {

  private String application;
  private ResourceScope scope;
  private String key;
  private Map<String, Object> qualifiers = new HashMap<>();

  public ResourceResolveContext() {
  }

  public ResourceResolveContext(ResourceScope scope, String key) {
    this(null, scope, key);
  }

  public ResourceResolveContext(String application, ResourceScope scope, String key) {
    this.application = application;
    this.scope = scope;
    this.key = normalize(key);
  }

  public String getApplication() {
    return application;
  }

  public ResourceResolveContext setApplication(String application) {
    this.application = application;
    return this;
  }

  public ResourceScope getScope() {
    return scope;
  }

  public ResourceResolveContext setScope(ResourceScope scope) {
    this.scope = scope;
    return this;
  }

  public String getKey() {
    return key;
  }

  public ResourceResolveContext setKey(String key) {
    this.key = normalize(key);
    return this;
  }

  public Map<String, Object> getQualifiers() {
    return qualifiers;
  }

  public ResourceResolveContext setQualifiers(Map<String, Object> qualifiers) {
    this.qualifiers = qualifiers == null ? new HashMap<>() : new HashMap<>(qualifiers);
    return this;
  }

  public ResourceResolveContext putQualifier(String key, Object value) {
    this.qualifiers.put(key, value);
    return this;
  }

  @JsonIgnore
  public String getScopedPath() {
    if (scope == null) {
      return key;
    }
    if (key == null || key.isEmpty()) {
      return scope.getValue();
    }
    return scope.getValue() + "/" + key;
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.startsWith("/") ? value.substring(1) : value;
    normalized = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    return normalized;
  }
}
