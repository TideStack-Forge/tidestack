package com.ouroboros.businessmodel.projection.behavior;

import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan;

/**
 * Projects the model-owned behavior contract into the standard bundle.
 */
@jakarta.annotation.Priority(60)
public class BehaviorMPS extends AbstractDefaultBusinessModelProcessStep {
  @Override
  public void process(ModelProjectionPipelineContext<com.ouroboros.businessmodel.DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    context.putModel(BusinessModelBehavior.class, model.getFullName(),
        model.getBehavior().withFeatureQueryPlans(context.getModelMap(BusinessActionFeatureQueryPlan.class)));
  }
}
