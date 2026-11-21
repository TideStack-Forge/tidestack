package com.ouroboros.businessmodel.projection.authority;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.security.authority.accesscontrol.AccessControlFilterDefine;
import com.ouroboros.uischema.schema.SchemaNode;

/**
 * @author liansz
 */
public class BusinessModelACFilterDefine implements AccessControlFilterDefine {
  private final String name;
  private final String label;
  private final Map<String, Object> extraProps = new LinkedHashMap<>();
  private List<SchemaNode> settingSchema;
  private String originalFieldType;
  private String originalField;

  public BusinessModelACFilterDefine(String name, String label, String originalFieldType, String originalField) {
    this.name = name;
    this.label = label;
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
    return null;
  }

  @Override
  public String getDataType() {
    return null;
  }

  @Override
  public String getOriginalFieldType() {
    return originalFieldType;
  }

  @Override
  public String getOriginalFieldName() {
    return originalField;
  }

  @Override
  public Map<String, Object> getExtraProps() {
    return extraProps;
  }

  @Override
  public Object getExtraProp(String key) {
    return extraProps.get(key);
  }

  public void setExtraProp(String key, Object value) {
    extraProps.put(key, value);
  }


  public void setSettingSchema(List<SchemaNode> settingSchema) {
    this.settingSchema = settingSchema;
  }

  @Override
  public List<SchemaNode> getSettingSchema() {
    return settingSchema;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AccessControlFilterDefine)) {
      return false;
    }
    return this.isEqual(other);
  }
}
