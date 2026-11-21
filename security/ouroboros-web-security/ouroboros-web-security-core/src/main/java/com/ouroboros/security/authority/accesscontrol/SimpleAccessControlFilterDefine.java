package com.ouroboros.security.authority.accesscontrol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.uischema.schema.SchemaNode;

public class SimpleAccessControlFilterDefine implements AccessControlFilterDefine {

  private String description;
  private String dataType;
  private String name;
  private String label;
  private String originalFieldType;
  private String originalField;
  private List<SchemaNode> settingSchema;

  @JsonIgnore
  private final Map<String, Object> extraProps = new LinkedHashMap<>();

  public SimpleAccessControlFilterDefine() {
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getLabel() {
    return label;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getDataType() {
    return dataType;
  }

  @Override
  public String getOriginalFieldType() {
    return originalFieldType;
  }

  @Override
  public String getOriginalFieldName() {
    return originalField;
  }

  @JsonAnyGetter
  @Override
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  @Override
  public Object getExtraProp(String key) {
    return extraProps.get(key);
  }

  @JsonAnySetter
  public void setExtraProp(String key, Object value) {
    extraProps.put(key, value);
  }

  @Override
  public List<SchemaNode> getSettingSchema() {
    return settingSchema;
  }

  @Override
  public boolean equals(Object other) {
    return this.isEqual(other);
  }
}
