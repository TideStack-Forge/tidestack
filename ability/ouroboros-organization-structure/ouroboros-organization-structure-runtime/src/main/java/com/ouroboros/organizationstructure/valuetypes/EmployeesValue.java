package com.ouroboros.organizationstructure.valuetypes;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
public class EmployeesValue implements ValueType<List>, ValueTypeBuilder<List, EmployeesValue> {

  @Override
  public String getName() {
    return "Employees";
  }

  @Override
  public String getLabel() {
    return "员工（多选）";
  }

  @Override
  public EmployeesValue build(DataModelField field) {
    return this;
  }

  @Override
  public Class<List> getType() {
    return List.class;
  }

  @Override
  public List<Map<String, Object>> convert(Object value) {
    if (!(value instanceof String) || ObjectUtils.isEmpty(value)) {
      return Collections.emptyList();
    }

    EmployeeAggregator service = SpringContextUtil.getBean(EmployeeAggregator.class);
    if (service == null) {
      return Collections.emptyList();
    }

    List<String> employeeIds = Arrays.asList(((String) value).split(","));

    // TODO: Maps::fromBean 无法识别只有 getter 的情况，在这里先通过 Json 转一下
    //       待优化
    return service.getByIds(employeeIds)
        .stream()
        .map(Json::toJsonString)
        .map(Json::toMap)
        .collect(Collectors.toList());
  }

  @Override
  public Object toPersistentValue(Object value) {
    if (value instanceof String) {
      return value;
    }

    if (value instanceof Collection<?> collection) {
      return Stream.concat(
          collection.stream()
              .filter(item -> item instanceof String)
              .map(item -> (String) item),
          Stream.concat(
              collection.stream()
                  .filter(item -> item instanceof Employee)
                  .map(item -> ((Employee) item).getId()),
              collection.stream()
                  .filter(item -> item instanceof Map)
                  .map(item -> (String) ((Map<?, ?>) item).get("id"))
          )
      ).collect(Collectors.joining(","));
    }

    return null;
  }
}
