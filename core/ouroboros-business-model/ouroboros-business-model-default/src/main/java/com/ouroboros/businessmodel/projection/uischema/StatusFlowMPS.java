package com.ouroboros.businessmodel.projection.uischema;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;

/**
 * 状态流页面UI Schema
 * Currently a no-op placeholder for future status flow UI schema generation.
 */
@Priority(34)
public class StatusFlowMPS extends AbstractDefaultBusinessModelProcessStep {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    // Intentional no-op: Placeholder for future status flow UI schema generation
  }

}
