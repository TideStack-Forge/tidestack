package com.ouroboros.rpc.dev.service.testing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.util.Json;

public record RpcModelTestRequest(String revision, Map<String, Object> variables, Map<String, String> configValues) {
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static RpcModelTestRequest parse(JsonNode json) {
    if (json == null || !json.isObject()) {
      throw new IllegalArgumentException("测试请求必须是 JSON 对象");
    }
    json.fieldNames().forEachRemaining(name -> {
      if (!Set.of("revision", "variables", "configValues").contains(name)) {
        throw new IllegalArgumentException("测试请求包含未知字段");
      }
    });
    if (!json.path("revision").isTextual() || json.path("revision").asText().isBlank()) {
      throw new IllegalArgumentException("缺少测试资源版本");
    }
    var variables = json.get("variables");
    if (variables != null && !variables.isObject()) {
      throw new IllegalArgumentException("variables 必须是 JSON 对象");
    }
    return new RpcModelTestRequest(json.path("revision").asText(), variables == null ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(Json.toMap(variables.toString()))), configuration(json));
  }

  static Map<String, String> configuration(JsonNode json) {
    var config = json.get("configValues");
    if (config != null && !config.isObject()) {
      throw new IllegalArgumentException("configValues 必须是 JSON 对象");
    }
    var overrides = new LinkedHashMap<String, String>();
    if (config != null) {
      config.properties().forEach(entry -> {
        if (!entry.getValue().isTextual()) {
          throw new IllegalArgumentException("配置覆盖值必须是字符串");
        }
        overrides.put(entry.getKey(), entry.getValue().asText());
      });
    }
    return Map.copyOf(overrides);
  }
}
