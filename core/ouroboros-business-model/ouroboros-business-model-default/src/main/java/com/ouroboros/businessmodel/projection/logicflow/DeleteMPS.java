package com.ouroboros.businessmodel.projection.logicflow;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.logicflow.prototypes.DeleteLogicModelPrototype;
import com.ouroboros.businessmodel.projection.logicflow.prototypes.ScriptLogicModelPrototype;
import com.ouroboros.logicflow.model.FlowModelMeta;

@Priority(43)
public class DeleteMPS extends AbstractDefaultBusinessModelProcessStep {
  final static String[] CONFIG_PATH = new String[]{"apis", "delete"};
  final static String LOGIC_FLOW_NAME = "delete";

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
      var subTables = model.getRelations().stream()
          .filter(BusinessModelRelation::getSubmitTogether)
          .collect(Collectors.toList());

      logicFlow = DeleteLogicModelPrototype.get(flowName, model)
          .configure(configMap)
          .configure("sourcePointerPrefix", "/apis/delete")
          .configure("modelName", model.getFullName())
          .configure("subTables", subTables)
          .getModel();
    } else {
      logicFlow = ScriptLogicModelPrototype.get(flowName, model)
          .configure(configMap)
          .configure("sourcePointerPrefix", "/apis/delete")
          .configure("modelName", model.getFullName())
          .getModel();
    }

    context.putModel(FlowModelMeta.class, flowName, logicFlow);
  }
}
