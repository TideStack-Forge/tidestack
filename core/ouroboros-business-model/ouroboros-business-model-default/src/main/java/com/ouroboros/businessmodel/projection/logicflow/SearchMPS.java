package com.ouroboros.businessmodel.projection.logicflow;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.logicflow.prototypes.ScriptLogicModelPrototype;
import com.ouroboros.businessmodel.projection.logicflow.prototypes.SearchLogicModelPrototype;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.logicflow.model.FlowModelMeta;

@Priority(40)
public class SearchMPS extends AbstractDefaultBusinessModelProcessStep {
  final static String[] CONFIG_PATH = new String[]{"apis", "search"};
  final static String LOGIC_FLOW_NAME = "search";

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
      // 默认排序
      var orderConfig = Optional.ofNullable(configMap.get("order"))
          .filter(Map.class::isInstance)
          .map(Map.class::cast)
          .filter(m -> ObjectUtils.isNotEmpty(m.get("field")))
          .orElseGet(Collections::emptyMap);

      var logicFlowProto = SearchLogicModelPrototype.get(flowName, model)
          .configure(configMap)
          .configure("sourcePointerPrefix", "/apis/search")
          .configure("workflowEnabled", model.getExtraPropDeep(Boolean.class, "workflow", "isEnabled").orElse(false))
          .configure("modelName", model.getFullName())
          .configure("modelPath", BusinessModelUtil.buildKebabCasePath(model))
          .configure("processId", model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElse(""));

      // 如果设置了默认排序则输入参数
      if (!orderConfig.isEmpty()) {
        var orderField = orderConfig.get("field");
        var orderDirection = (String) orderConfig.getOrDefault("direction", "ASC");
        var defaultOrder = Optional.ofNullable(orderField)
            .map(Object::toString)
            .filter(s -> !s.isEmpty())
            .map(k -> Collections.singletonMap(k, orderDirection))
            .orElse(null);
        logicFlowProto.configure("defaultOrder", defaultOrder);
      }

      // 生成逻辑流
      logicFlow = logicFlowProto.getModel();
    } else {
      logicFlow = ScriptLogicModelPrototype.get(flowName, model)
          .configure(configMap)
          .configure("sourcePointerPrefix", "/apis/search")
          .configure("modelName", model.getFullName())
          .getModel();
    }

    context.putModel(FlowModelMeta.class, flowName, logicFlow);
  }
}
