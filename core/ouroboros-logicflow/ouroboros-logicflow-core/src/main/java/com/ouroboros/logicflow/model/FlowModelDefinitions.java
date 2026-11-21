package com.ouroboros.logicflow.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.logicflow.util.LogicFlowJson;

/** Formal persistence and fingerprint format. Validation is derived, array order is significant. */
public final class FlowModelDefinitions {
  @JsonIgnoreProperties({"validationState", "validationSummary", "validationErrors",
      "validationDiagnostics", "lastValidatedTime", "inputDataTypeEnum", "outputDataTypeEnum"})
  private abstract static class FormalDefinition { }

  private static final ObjectMapper mapper = LogicFlowJson.OBJECT_MAPPER.copy()
      .addMixIn(FlowModelMeta.class, FormalDefinition.class);

  private FlowModelDefinitions() { }

  public static String write(FlowModelMeta meta) {
    try {
      return mapper.writeValueAsString(canonical(mapper.valueToTree(meta)));
    } catch (java.io.IOException failure) {
      throw new IllegalArgumentException("逻辑流正式定义无法序列化", failure);
    }
  }

  public static FlowModelMeta read(String definition) {
    try {
      return mapper.readValue(definition, FlowModelMeta.class);
    } catch (java.io.IOException failure) {
      throw new IllegalArgumentException("逻辑流正式定义无法解析", failure);
    }
  }

  public static FlowModelMeta copy(FlowModelMeta meta) {
    return read(write(meta));
  }

  public static String fingerprint(Object value) {
    try {
      return hash(mapper.writeValueAsString(canonical(mapper.valueToTree(value))));
    } catch (java.io.IOException failure) {
      throw new IllegalArgumentException("逻辑流指纹无法计算", failure);
    }
  }

  public static String hash(String definition) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(definition.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 不可用", failure);
    }
  }

  /** Same case equivalence as String.compareToIgnoreCase, independent of database collation. */
  public static String nameKey(String name) {
    var key = new StringBuilder();
    name.codePoints().map(cp -> Character.toLowerCase(Character.toUpperCase(cp))).forEach(key::appendCodePoint);
    return key.toString();
  }

  public static Map<String, String> freeze(Map<String, FlowModelMeta> metas) {
    var definitions = new TreeMap<String, String>(String::compareToIgnoreCase);
    metas.forEach((name, meta) -> definitions.put(name, write(meta)));
    return Collections.unmodifiableMap(definitions);
  }

  public static Map<String, FlowModelMeta> thaw(Map<String, String> definitions) {
    var metas = new LinkedHashMap<String, FlowModelMeta>();
    definitions.forEach((name, definition) -> metas.put(name, read(definition)));
    return Collections.unmodifiableMap(metas);
  }

  private static JsonNode canonical(JsonNode node) {
    if (node.isObject()) {
      var sorted = new TreeMap<String, JsonNode>();
      node.properties().forEach(entry -> sorted.put(entry.getKey(), canonical(entry.getValue())));
      var result = mapper.createObjectNode();
      sorted.forEach(result::set);
      return result;
    }
    if (node.isArray()) {
      var result = mapper.createArrayNode();
      node.forEach(item -> result.add(canonical(item)));
      return result;
    }
    return node;
  }
}
