package com.ouroboros.businessmodel.projection.workflow;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;

public abstract class AbstractWorkflowMPS extends AbstractDefaultBusinessModelProcessStep {
  @Override
  public final void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    if (!shouldProcess(model)) {
      return;
    }
    doProcess(context);
  }

  protected boolean shouldProcess(DefaultBusinessModel model) {
    return model.getExtraPropDeep(Boolean.class, "workflow", "isEnabled").orElse(false);
  }

  protected abstract void doProcess(ModelProjectionPipelineContext<DefaultBusinessModel> context);
}
