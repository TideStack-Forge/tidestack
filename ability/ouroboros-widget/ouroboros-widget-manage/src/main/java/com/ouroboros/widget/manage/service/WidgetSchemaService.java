package com.ouroboros.widget.manage.service;

import java.util.List;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.widget.WidgetDefinitionGenerator;
import com.ouroboros.widget.WidgetSchema;
import com.ouroboros.widget.manage.model.WidgetSchemaModel;

public interface WidgetSchemaService {
  boolean checkNameAvailable(String name);
  List<WidgetDefinitionGenerator> getWidgetDefinitionGenerators();
  List<SchemaNode> getFormItems(String generatorName);
  Try<Void> addWidgetSchema(WidgetSchema widgetSchema);
  Try<Void> updateWidgetSchema(String id, WidgetSchema widgetSchema);
  Try<Void> deleteWidgetSchema(String id);
/**
 * 根据组件ID获取组件的Schema信息
 * @param id 组件的唯一标识符
 * @return 返回对应的WidgetSchema对象，包含组件的结构和配置信息
 */
  Optional<WidgetSchema> getWidgetSchema(String id);
  List<WidgetSchemaModel> getAllWidgetSchemas();
}
