package com.ouroboros.businessmodel.projection.logicflow;

import java.util.Collections;
import java.util.Map;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.logicflow.prototypes.GetOneLogicModelPrototype;
import com.ouroboros.businessmodel.projection.logicflow.prototypes.ScriptLogicModelPrototype;
import com.ouroboros.logicflow.model.FlowModelMeta;

@Priority(40)
public class GetOneMPS extends AbstractDefaultBusinessModelProcessStep {
  final static String[] CONFIG_PATH = new String[]{"apis", "getOne"};
  final static String LOGIC_FLOW_NAME = "getOne";

  @Override
  @SuppressWarnings("unchecked")
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    if (!context.getAttribute(Boolean.class, "hasDataModel", false)) {
      return;
    }
    var model = context.getSourceModel();
    var configMap = model.getExtraPropDeep(Map.class, CONFIG_PATH).orElseGet(Collections::emptyMap);
    var flowName = model.getFullName() + "." + LOGIC_FLOW_NAME;

    // 如果isAuto，为false，则表示使用脚本逻辑
    FlowModelMeta logicFlow;
    if ((boolean) configMap.getOrDefault("isAuto", true)) {
      logicFlow = GetOneLogicModelPrototype.get(flowName, model)
          .configure(configMap)
          .configure("sourcePointerPrefix", "/apis/getOne")
          .configure("modelName", model.getFullName())
          .getModel();
    } else {
      logicFlow = ScriptLogicModelPrototype.get(flowName, model)
          .configure(configMap)
          .configure("sourcePointerPrefix", "/apis/getOne")
          .configure("modelName", model.getFullName())
          .getModel();
    }

    context.putModel(FlowModelMeta.class, flowName, logicFlow);
  }
}
