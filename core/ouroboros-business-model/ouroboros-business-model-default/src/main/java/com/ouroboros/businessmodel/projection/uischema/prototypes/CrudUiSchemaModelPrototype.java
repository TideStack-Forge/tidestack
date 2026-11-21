package com.ouroboros.businessmodel.projection.uischema.prototypes;

import java.util.Map;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelPrototype;
import com.ouroboros.uischema.schema.SchemaNode;

public class CrudUiSchemaModelPrototype extends AbstractDefaultBusinessModelPrototype<SchemaNode, CrudUiSchemaModelPrototype> {
  private CrudUiSchemaModelPrototype(DefaultBusinessModel businessModel) {
    super(businessModel);
  }

  public static CrudUiSchemaModelPrototype get(DefaultBusinessModel businessModel) {
    return new CrudUiSchemaModelPrototype(businessModel);
  }

  /**
   * 根据对象属性配置模型
   */
  @Override
  public CrudUiSchemaModelPrototype configure(Map<String, Object> indexConfig) {
    config.putAll(indexConfig);
    return this;
  }

  @Override
  public SchemaNode getModel() {
    var columnProcessor = new CrudUiSchemaColumnProcessor(businessModel, config);
    var actionProcessor = new CrudUiSchemaActionProcessor(businessModel, config);
    return new CrudUiSchemaBuilder(businessModel, config, columnProcessor, actionProcessor).build();
  }
}
