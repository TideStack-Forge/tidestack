package com.ouroboros.organizationstructure.valuetypes;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.ValueType;
import com.ouroboros.data.model.ValueTypeBuilder;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
public class EmployeeValue
    implements ValueType<Map<String, Object>>, ValueTypeBuilder<Map<String, Object>, EmployeeValue> {

  @Override
  public String getName() {
    return "Employee";
  }

  @Override
  public String getLabel() {
    return "员工";
  }

  @Override
  public EmployeeValue build(DataModelField field) {
    return this;
  }

  @Override
  public Class<Map<String, Object>> getType() {
    return (Class<Map<String, Object>>) Collections.emptyMap().getClass();
  }

  @Override
  public Map<String, Object> convert(Object value) {
    if (!(value instanceof String) || ObjectUtils.isEmpty(value)) {
      return null;
    }

    EmployeeAggregator service = SpringContextUtil.getBean(EmployeeAggregator.class);
    if (service == null) {
      return null;
    }
    // TODO: Maps::fromBean 无法识别只有 getter 的情况，在这里先通过 Json 转一下
    //       待优化
    return service.getById((String) value)
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

    if (value instanceof Employee department) {
      return department.getId();
    }

    return null;
  }
}
