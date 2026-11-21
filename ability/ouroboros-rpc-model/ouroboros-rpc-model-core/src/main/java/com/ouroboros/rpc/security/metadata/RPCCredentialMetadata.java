package com.ouroboros.rpc.security.metadata;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 **/
public class RPCCredentialMetadata {

  /**
   * 英文名称
   */
  private String name;

  /**
   * 中文名称
   */
  private String title;

  /**
   * 类型
   */
  private String type;

  /**
   * 是否启用
   */
  @JsonProperty("isEnabled")
  private boolean isEnabled;

  /**
   * 描述
   */
  private String description;
  /** Optional invocation contracts for authoring; never inferred from request configuration. */
  private Map<String, Object> inputSchema = Map.of();
  private Map<String, Object> outputSchema = Map.of();

  /**
   * 额外属性
   */
  private Map<String, Object> extraProps = new HashMap<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @JsonProperty("isEnabled")
  public boolean isEnabled() {
    return isEnabled;
  }

  public void setEnabled(boolean enabled) {
    isEnabled = enabled;
  }

  public Map<String, Object> getInputSchema() { return java.util.Collections.unmodifiableMap(inputSchema); }

  public void setInputSchema(Map<String, Object> schema) { inputSchema = new java.util.LinkedHashMap<>(schema == null ? Map.of() : schema); }

  public Map<String, Object> getOutputSchema() { return java.util.Collections.unmodifiableMap(outputSchema); }

  public void setOutputSchema(Map<String, Object> schema) { outputSchema = new java.util.LinkedHashMap<>(schema == null ? Map.of() : schema); }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  public void setExtraProps(Map<String, Object> extraProps) {
    this.extraProps = extraProps;
  }

  @JsonAnySetter
  public void setExtraProp(String key, Object value) {
    if ("fullName".equals(key)) {
      return;
    }

    extraProps.put(key, value);
  }

  public Optional<Object> getExtraPropDeep(String... pathKey) {
    return Maps.Hierarchical.getValue(getExtraProps(), Arrays.asList(pathKey));
  }

  public <T> Optional<T> getExtraPropDeep(Class<T> clazz, String... pathKey) {
    return getExtraPropDeep(pathKey).map(v -> {
      if (clazz.isInstance(v)) {
        return clazz.cast(v);
      }
      return null;
    });
  }
}
