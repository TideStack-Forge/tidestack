package com.ouroboros.businessmodel.projection.logicflow.prototypes;

import java.util.Collections;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelPrototype;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.logicflow.model.FlowModelMeta;

public class SearchLogicModelPrototype extends AbstractDefaultBusinessModelPrototype<FlowModelMeta, SearchLogicModelPrototype> {
  private SearchLogicModelPrototype(String flowName, DefaultBusinessModel model) {
    super(model);
    config.put("fullName", flowName);
  }

  public static SearchLogicModelPrototype get(String flowName, DefaultBusinessModel model) {
    return new SearchLogicModelPrototype(flowName, model);
  }

  @Override
  public FlowModelMeta getModel() {
    preprocessDescription();
    preprocessDefaultFilter();
    preprocessPopulate();
    return PrototypeTemplate.generate("logic-flow/search", config, FlowModelMeta.class);
  }

  private void preprocessDescription() {
    config.put("description", "分页查询" + businessModel.getTitle());
  }

  private void preprocessPopulate() {
    // Intentional no-op hook: Subclasses can override to customize populate configuration
  }

  private void preprocessDefaultFilter() {
    config.put("defaultFilter", Collections.singletonMap("isDeleted", "false"));
  }
}
