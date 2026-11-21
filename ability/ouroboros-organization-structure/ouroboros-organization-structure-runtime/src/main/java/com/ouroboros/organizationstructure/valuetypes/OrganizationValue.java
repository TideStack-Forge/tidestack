package com.ouroboros.organizationstructure.valuetypes;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.ValueType;
import com.ouroboros.data.model.ValueTypeBuilder;
import com.ouroboros.organizationstructure.OrganizationAggregator;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
public class OrganizationValue
    implements ValueType<Map<String, Object>>, ValueTypeBuilder<Map<String, Object>, OrganizationValue> {

  @Override
  public String getName() {
    return "Organization";
  }

  @Override
  public String getLabel() {
    return "组织架构";
  }

  @Override
  public OrganizationValue build(DataModelField field) {
    return this;
  }

  @Override
  public Class<Map<String, Object>> getType() {
    return (Class<Map<String, Object>>) Collections.emptyMap().getClass();
  }

  @Override
  public Map<String, Object> convert(Object value) {
    if (!(value instanceof String id) || ObjectUtils.isEmpty(value)) {
      return null;
    }

    OrganizationAggregator service = SpringContextUtil.getBean(OrganizationAggregator.class);
    if (service == null) {
      return null;
    }

    // TODO: Maps::fromBean 无法识别只有 getter 的情况，在这里先通过 Json 转一下
    //       待优化
    return service.getById(id)
        .map(Json::toJsonString)
        .map(Json::toMap)
        .orElse(null);
  }

  @Override
  public Object toPersistentValue(Object value) {
    if (value instanceof String) {
      return value;
    }

    if (value instanceof Map map) {
      return map.get("id");
    }

    if (value instanceof OrganizationNode node) {
      return node.getId();
    }

    return null;
  }
}
