package com.ouroboros.businessmodel.projection.logicflow.prototypes;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelPrototype;
import com.ouroboros.businessmodel.projection.support.BusinessModelSchemaSupport;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.logicflow.model.FlowModelMeta;

public class AddLogicModelPrototype extends AbstractDefaultBusinessModelPrototype<FlowModelMeta, AddLogicModelPrototype> {
  private AddLogicModelPrototype(String flowName, DefaultBusinessModel model) {
    super(model);
    config.put("fullName", flowName);
  }

  public static AddLogicModelPrototype get(String flowName, DefaultBusinessModel model) {
    return new AddLogicModelPrototype(flowName, model);
  }

  @Override
  public FlowModelMeta getModel() {
    preprocessDescription();
    preprocessValidationSchema();
    return PrototypeTemplate.generate("logic-flow/add", config, FlowModelMeta.class);
  }

  private void preprocessDescription() {
    config.put("description", "添加" + businessModel.getTitle() + "单条数据");
  }

  private void preprocessValidationSchema() {
    config.put("validationSchema", BusinessModelSchemaSupport.createValidationSchema(businessModel));
  }

}
