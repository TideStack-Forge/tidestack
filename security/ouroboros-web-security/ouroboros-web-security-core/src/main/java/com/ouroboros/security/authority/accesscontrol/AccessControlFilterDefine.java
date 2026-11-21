package com.ouroboros.security.authority.accesscontrol;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ouroboros.uischema.schema.SchemaNode;

/**
 * 过滤器定义，用于描述过滤器
 */
@JsonDeserialize(as = SimpleAccessControlFilterDefine.class)
public interface AccessControlFilterDefine {

  String getName();

  String getLabel();

  /**
   * 获取描述
   *
   * @return
   */
  String getDescription();

  /**
   * 获取数据类型
   *
   * @return
   */
  String getDataType();

  String getOriginalFieldType();

  String getOriginalFieldName();

  List<SchemaNode> getSettingSchema();

  Map<String, Object> getExtraProps();

  Object getExtraProp(String key);

  default boolean isEqual(Object other) {
    if (other == null) {
      return false;
    }
    if (other == this) {
      return true;
    }
    if (other instanceof AccessControlFilterDefine otherDefine) {
      return otherDefine.getName().equals(this.getName());
    }
    return false;
  }

}
