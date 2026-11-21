package com.ouroboros.businessmodel.projection.logicflow;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.logicflow.childcollection.ChildCollectionSyncPlanCompiler;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.logicflow.prototypes.ScriptLogicModelPrototype;
import com.ouroboros.businessmodel.projection.logicflow.prototypes.UpdateLogicModelPrototype;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.logicflow.model.FlowModelMeta;

@Priority(42)
public class UpdateMPS extends AbstractDefaultBusinessModelProcessStep {
  final static String[] CONFIG_PATH = new String[]{"apis", "update"};
  final static String LOGIC_FLOW_NAME = "update";

  @Override
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
      logicFlow = UpdateLogicModelPrototype.get(flowName, model)
          .configure(configMap)
          .configure("sourcePointerPrefix", "/apis/update")
          .configure("modelName", model.getFullName())
          .configure("childCollectionSyncPlans", new ChildCollectionSyncPlanCompiler()
              .compile(model, modelName -> resolveDataModelMeta(context, modelName)))
          .getModel();
    } else {
      logicFlow = ScriptLogicModelPrototype.get(flowName, model)
          .configure(configMap)
          .configure("sourcePointerPrefix", "/apis/update")
          .configure("modelName", model.getFullName())
          .getModel();
    }

    context.putModel(FlowModelMeta.class, flowName, logicFlow);
  }

  private Optional<DataModelMeta> resolveDataModelMeta(ModelProjectionPipelineContext<DefaultBusinessModel> context, String modelName) {
    return context.getModel(DataModelMeta.class, modelName)
        .or(() -> findRelatedModel(context.getSourceModel(), modelName, new HashSet<>())
            .flatMap(relatedModel -> relatedModel.buildModelBundle().getModel(DataModelMeta.class, modelName)));
  }

  private Optional<DefaultBusinessModel> findRelatedModel(DefaultBusinessModel model, String modelName, Set<String> visitedModels) {
    if (!visitedModels.add(model.getFullName())) {
      return Optional.empty();
    }
    for (var relation : model.getRelations()) {
      var relatedModel = relation.getRelatedModel();
      if (relatedModel == null) {
        continue;
      }
      if (modelName.equalsIgnoreCase(relatedModel.getFullName())) {
        return Optional.of(relatedModel);
      }
      var nested = findRelatedModel(relatedModel, modelName, visitedModels);
      if (nested.isPresent()) {
        return nested;
      }
    }
    return Optional.empty();
  }
}
