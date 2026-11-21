package com.ouroboros.configuration.metadata;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Json;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 */
public class ConfigurationMetadata implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * 唯一键
   */
  private String key;

  /**
   * 标签
   */
  private String label;

  /**
   * 描述
   */
  private String description;

  /**
   * 允许修改
   */
  private boolean modifiable;

  /**
   * 允许为空
   */
  private boolean nullable;

  /**
   * 值类型
   */
  private ConfigValueType valueType;

  /**
   * 默认值
   */
  private String defaultValue;

  /**
   * 配置所属分组路径
   */
  private String groupPath;

  /**
   * 同组内排序
   */
  private Integer order;

  /**
   * 范围
   */
  private ConfigScope scope;

  /**
   * 是否过时
   */
  private boolean obsolete;

  private SchemaNode editorSchema;

  /** 专用管理页面；声明后由该入口接管，系统配置工作台不再生成编辑表单。 */
  private String managementEntry;

  /**
   * 过时原因
   */
  private String obsoleteCause;

  private Map<String, Object> extraProps = new LinkedHashMap<>();

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getLabel() {
    return label;
  }

  public ConfigurationMetadata setLabel(String label) {
    this.label = label;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isModifiable() {
    return modifiable;
  }

  public void setModifiable(boolean modifiable) {
    this.modifiable = modifiable;
  }

  public boolean isNullable() {
    return nullable;
  }

  public void setNullable(boolean nullable) {
    this.nullable = nullable;
  }

  public ConfigValueType getValueType() {
    return valueType;
  }

  public void setValueType(ConfigValueType valueType) {
    this.valueType = valueType;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  @JsonSetter("defaultValue")
  public void setDefaultValueFromJson(Object defaultValue) {
    if (defaultValue == null) {
      this.defaultValue = null;
      return;
    }
    if (defaultValue instanceof String stringValue) {
      this.defaultValue = stringValue;
      return;
    }
    this.defaultValue = Json.toJsonString(defaultValue);
  }

  public String getGroupPath() {
    return groupPath;
  }

  public void setGroupPath(String groupPath) {
    this.groupPath = groupPath;
  }

  public Integer getOrder() {
    return order;
  }

  public void setOrder(Integer order) {
    this.order = order;
  }

  public ConfigScope getScope() {
    return scope == null ? ConfigScope.BACKEND : scope;
  }

  public void setScope(ConfigScope scope) {
    this.scope = scope;
  }

  public boolean isObsolete() {
    return obsolete;
  }

  public void setObsolete(boolean obsolete) {
    this.obsolete = obsolete;
  }

  public String getObsoleteCause() {
    return obsoleteCause;
  }

  public void setObsoleteCause(String obsoleteCause) {
    this.obsoleteCause = obsoleteCause;
  }

  public SchemaNode getEditorSchema() {
    return editorSchema;
  }

  public void setEditorSchema(SchemaNode editorSchema) {
    this.editorSchema = editorSchema;
  }

  public String getManagementEntry() {
    return managementEntry;
  }

  public void setManagementEntry(String managementEntry) {
    if (managementEntry != null && (StringUtils.isBlank(managementEntry)
        || !managementEntry.startsWith("/") || managementEntry.startsWith("//"))) {
      throw new com.ouroboros.exception.PlatformException(
          "[CONFIGURATION_MANAGEMENT_ENTRY_INVALID] 配置管理入口必须为站内绝对路径");
    }
    this.managementEntry = managementEntry;
  }

  @JsonIgnore
  public boolean isManagementTakenOver() {
    return getManagementEntry() != null;
  }

  @JsonAnyGetter
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  @JsonAnySetter
  public void setExtraProp(String key, Object value) {
    extraProps.put(key, value);
  }

  public Optional<Object> getExtraProp(String key) {
    return Optional.ofNullable(extraProps.get(key));
  }

  @JsonIgnore
  public boolean isManagementProtected() {
    return Boolean.TRUE.equals(getExtraProp("managementAccessPolicyRequired").orElse(false));
  }

  /** Opt-in: RestoreDefaults removes the stored value instead of writing a default snapshot. */
  @JsonIgnore
  public boolean isRestoreDefaultRemovesOverride() {
    return Boolean.TRUE.equals(getExtraProp("restoreDefaultRemovesOverride").orElse(false));
  }

  public ConfigurationMetadata merge(ConfigurationMetadata another) {
    if (another == null) {
      return this;
    }
    if (isManagementProtected() || another.isManagementProtected()) {
      throw new com.ouroboros.exception.PlatformException(
          "[CONFIGURATION_PROTECTED_METADATA] 受保护配置定义不允许同键覆盖: " + getKey());
    }
    return new Merged(this, another);
  }

  static class Merged extends ConfigurationMetadata {
    @JsonIgnore
    private ConfigurationMetadata metadata;
    @JsonIgnore
    private ConfigurationMetadata another;

    public Merged(ConfigurationMetadata metadata, ConfigurationMetadata another) {
      this.metadata = metadata;
      this.another = another;
    }

    @Override
    public String getKey() {
      return another.getKey();
    }

    @Override
    public String getLabel() {
      return StringUtils.defaultIfEmpty(another.getLabel(), metadata.getLabel());
    }

    @Override
    public String getDescription() {
      return StringUtils.defaultIfEmpty(another.getDescription(), metadata.getDescription());
    }

    @Override
    public boolean isModifiable() {
      return another.isModifiable();
    }

    @Override
    public boolean isNullable() {
      return another.isNullable();
    }

    @Override
    public ConfigValueType getValueType() {
      return another.getValueType();
    }

    @Override
    public String getDefaultValue() {
      return StringUtils.defaultIfEmpty(another.getDefaultValue(), metadata.getDefaultValue());
    }

    @Override
    public String getGroupPath() {
      return StringUtils.defaultIfEmpty(another.getGroupPath(), metadata.getGroupPath());
    }

    @Override
    public Integer getOrder() {
      return ObjectUtils.defaultIfNull(another.getOrder(), metadata.getOrder());
    }

    @Override
    public ConfigScope getScope() {
      return another.getScope();
    }

    @Override
    public String getObsoleteCause() {
      return StringUtils.defaultIfEmpty(another.getObsoleteCause(), metadata.getObsoleteCause());
    }

    @Override
    public SchemaNode getEditorSchema() {
      return ObjectUtils.defaultIfNull(another.getEditorSchema(), metadata.getEditorSchema());
    }

    @Override
    public String getManagementEntry() {
      return ObjectUtils.defaultIfNull(another.getManagementEntry(), metadata.getManagementEntry());
    }

    @JsonAnyGetter
    @Override
    public Map<String, Object> getExtraProps() {
      return Maps.merge(metadata.getExtraProps(), another.getExtraProps());
    }

    @Override
    public Optional<Object> getExtraProp(String key) {
      var value = another.getExtraProp(key)
          .orElseGet(() -> metadata.getExtraProp(key).orElse(null));
      return Optional.ofNullable(value);
    }
  }
}
