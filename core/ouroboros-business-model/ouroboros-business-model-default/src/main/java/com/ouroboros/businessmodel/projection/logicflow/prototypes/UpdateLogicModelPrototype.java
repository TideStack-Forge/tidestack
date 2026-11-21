package com.ouroboros.businessmodel.projection.logicflow.prototypes;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelPrototype;
import com.ouroboros.businessmodel.projection.support.BusinessModelSchemaSupport;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.logicflow.model.FlowModelMeta;

public class UpdateLogicModelPrototype extends AbstractDefaultBusinessModelPrototype<FlowModelMeta, UpdateLogicModelPrototype> {

  private UpdateLogicModelPrototype(String flowName, DefaultBusinessModel model) {
    super(model);
    config.put("fullName", flowName);
  }

  public static UpdateLogicModelPrototype get(String flowName, DefaultBusinessModel model) {
    return new UpdateLogicModelPrototype(flowName, model);
  }

  @Override
  public FlowModelMeta getModel() {
    preprocessDescription();
    preprocessValidationSchema();

    return PrototypeTemplate.generate("logic-flow/update", config, FlowModelMeta.class);
  }

  private void preprocessDescription() {
    config.put("description", "更新" + businessModel.getTitle() + "单条数据");
  }

  private void preprocessValidationSchema() {
    config.put("validationSchema", BusinessModelSchemaSupport.updateValidationSchema(businessModel));
  }
}
