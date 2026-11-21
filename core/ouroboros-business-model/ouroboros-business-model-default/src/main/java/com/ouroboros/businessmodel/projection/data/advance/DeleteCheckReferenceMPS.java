package com.ouroboros.businessmodel.projection.data.advance;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;
import com.ouroboros.data.model.PluginDescriptor;

/**
 * @author liansz
 **/
@Priority(1)
public class DeleteCheckReferenceMPS extends AbstractDataMPS {
  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    context.getSourceModel().getExtraPropDeep(Boolean.class, "apis", "delete", "isForceCheckReference")
        .filter(v -> v)
        .ifPresent(v -> addPluginDescriptorToMainDataModel(context, new PluginDescriptor("ReferenceChecker")));
  }
}
