package com.ouroboros.businessmodel.projection.logicflow.prototypes;

import java.util.HashMap;
import java.util.Map;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.projection.ModelPrototype;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.logicflow.model.FlowModelMeta;

/**
 * @author liansz
 */
public class ScriptLogicModelPrototype implements ModelPrototype<FlowModelMeta, ScriptLogicModelPrototype> {
  Map<String, Object> config = new HashMap<>();
  String flowName;
  BusinessModel model;

  private ScriptLogicModelPrototype(String flowName, BusinessModel model) {
    this.model = model;
    config.put("fullName", flowName);
  }

  public static ScriptLogicModelPrototype get(String flowName, BusinessModel model) {
    return new ScriptLogicModelPrototype(flowName, model);
  }

  @Override
  public ScriptLogicModelPrototype configure(Map<String, Object> config) {
    this.config.putAll(config);
    return this;
  }

  @Override
  public FlowModelMeta getModel() {
    return buildModel();
  }

  /**
   * 构建模型
   *
   * @return
   */
  private FlowModelMeta buildModel() {
    return PrototypeTemplate.generate("logic-flow/singleScript", config, FlowModelMeta.class);
  }
}
