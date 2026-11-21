package com.ouroboros.app.datamodel.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelField;
import com.ouroboros.data.model.DataModelPlugin;
import com.ouroboros.data.model.DataModelPluginBuilder;
import com.ouroboros.data.model.DataModelPluginContext;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 **/
public class EmployeeSetterDataModelPlugin implements DataModelPlugin {

  private final String field;

  public EmployeeSetterDataModelPlugin(String field) {
    this.field = field;
  }

  @Override
  public Try<Record> insert(Map<String, Object> data, DataModelPluginContext context) {
    data = new HashMap<>(data);
    data.put(field, getOperateBy());
    return DataModelPlugin.super.insert(data, context);
  }

  @Override
  public Try<RecordList> batchInsert(List<Map<String, Object>> dataList, DataModelPluginContext context) {
    return DataModelPlugin.super.batchInsert(
        dataList.stream().map(HashMap::new).peek(map -> map.put(field, getOperateBy())).collect(Collectors.toList()),
        context
    );
  }

  @Override
  public Try<Long> update(SExpression<Boolean> where, Map<String, Object> data, DataModelPluginContext context) {
    data = new HashMap<>(data);
    Maps.remove(data, field);
    return DataModelPlugin.super.update(where, data, context);
  }

  private String getOperateBy() {
    return OrganizationContextHolder.getContext()
        .flatMap(context -> context.getEmployee().map(Employee::getId))
        .orElse(null);
  }

  public static class Builder implements DataModelPluginBuilder {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public boolean support(String name) {
      return "EmployeeSetter".equalsIgnoreCase(name);
    }

    @Override
    public Optional<DataModelPlugin> build(DataModel dataModel, Map<String, Object> configMap) {
      String field = (String) configMap.getOrDefault("field", "createdByEmployee");

      List<String> fieldNames = dataModel.getFields().stream().map(DataModelField::getName).collect(Collectors.toList());
      if (!fieldNames.contains(field)) {
        logger.error("数据模型 {} 不存在员工设置器字段 {} 请检查！", dataModel.getFullName(), field);
        return Optional.empty();
      }

      return Optional.of(new EmployeeSetterDataModelPlugin(field));
    }
  }
}
