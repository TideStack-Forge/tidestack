package com.ouroboros.rpc.metadata;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.ouroboros.exception.BusinessException;

/**
 * @author liansz
 **/
public class RPCModelMetadata {

  /**
   * 分组的完整可读路径，以 / 分隔层级；空值表示未分组。
   */
  private String group;

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
   * 认证器
   */
  private String authCredential;

  /**
   * 是否启用
   */
  @JsonProperty("isEnabled")
  private boolean isEnabled;

  /**
   * 请求
   */
  private Map<String, Object> request;

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

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    if (group == null || group.isBlank()) {
      this.group = null;
      return;
    }
    var segments = Arrays.stream(group.split("/", -1)).map(String::strip).toList();
    if (segments.stream().anyMatch(String::isEmpty)) {
      throw new BusinessException("RPC 分组路径不能包含空层级");
    }
    this.group = String.join("/", segments);
  }

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

  public String getAuthCredential() {
    return authCredential;
  }

  public RPCModelMetadata setAuthCredential(String authCredential) {
    this.authCredential = authCredential;
    return this;
  }

  @JsonProperty("isEnabled")
  public boolean isEnabled() {
    return isEnabled;
  }

  public void setEnabled(boolean enabled) {
    isEnabled = enabled;
  }

  public Map<String, Object> getRequest() {
    return request;
  }

  public void setRequest(Map<String, Object> request) {
    this.request = request;
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
}
