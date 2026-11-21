package com.ouroboros.businessmodel.projection.logicflow.prototypes;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelPrototype;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.logicflow.model.FlowModelMeta;

public class GetOneLogicModelPrototype extends AbstractDefaultBusinessModelPrototype<FlowModelMeta, GetOneLogicModelPrototype> {

  private GetOneLogicModelPrototype(String flowName, DefaultBusinessModel model) {
    super(model);
    config.put("fullName", flowName);
  }

  public static GetOneLogicModelPrototype get(String flowName, DefaultBusinessModel model) {
    return new GetOneLogicModelPrototype(flowName, model);
  }

  @Override
  public FlowModelMeta getModel() {
    preprocessDescription();
    preprocessDefaultFilter();
    preprocessPopulate();

    return PrototypeTemplate.generate("logic-flow/getOne", config, FlowModelMeta.class);
  }

  private void preprocessPopulate() {
    // Intentional no-op hook: Subclasses can override to customize populate configuration
  }

  private void preprocessDefaultFilter() {
    // Intentional no-op hook: Subclasses can override to customize default filter
  }

  private void preprocessDescription() {
    config.put("description", "根据ID获取" + businessModel.getTitle() + "单条数据");
  }
}
