package com.ouroboros.businessmodel.projection.logicflow;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.util.Maps;

public class BatchDeleteMPS extends AbstractDefaultBusinessModelProcessStep {
  private static final String FLOW_NAME = "批量删除";
  private static final String LOGIC_SUFFIX = ".batchDelete";

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var isEnabled = model.getExtraPropDeep(Boolean.class, "features", "isEnabledBatchDelete").orElse(false);
    if (!isEnabled) {
      return;
    }

    var modelName = model.getFullName();
    var modelTitle = model.getTitle();
    var fullName = model.getFullName() + LOGIC_SUFFIX;

    var params = Maps.<String, Object>builder()
        .put("fullName", fullName)
        .put("modelName", modelName)
        .put("modelTitle", modelTitle)
        .build();

    var flowModel = PrototypeTemplate.generate("logic-flow/batchDelete", params, FlowModelMeta.class);
    context.putModel(FlowModelMeta.class, fullName, flowModel);
  }
}
