package com.ouroboros.businessmodel.projection.data.plugin;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.data.AbstractDataMPS;
import com.ouroboros.data.model.DataModelMeta;

/**
 * Integrates business model feature plugins into the business model to data model projection pipeline.
 * <p>
 * The projection flow is:
 * <ol>
 * <li>{@link com.ouroboros.businessmodel.projection.data.basic.BasicMPS} creates the base {@link DataModelMeta}</li>
 * <li>This step resolves enabled business model feature plugins from the registry</li>
 * <li>Each plugin projector writes the feature's normalized data-model projection output</li>
 * <li>Soft-delete projection writes decorator input for {@code LogicalDeleteDecorator}</li>
 * <li>Archive-delete projection writes the {@code ArchiveDelete} plugin descriptor directly</li>
 * <li>The decorated data model is then used to assemble the final runtime plugin chain</li>
 * </ol>
 * <p>
 * This step keeps delete-plugin selection in the business-model projection layer. Data-layer
 * decorators may still enrich metadata, but they no longer decide between delete strategies.
 */
@Priority(50)
public class PluginFeaturesMPS extends AbstractDataMPS {
  private static final Logger logger = LoggerFactory.getLogger(PluginFeaturesMPS.class);

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var sourceModel = context.getSourceModel();
    var dataModelOptional = context.getModel(DataModelMeta.class, sourceModel.getFullName());
    if (!dataModelOptional.isPresent()) {
      logger.debug("Skip plugin projection because no data model exists for business model {}", sourceModel.getFullName());
      return;
    }

    for (var plan : sourceModel.getFeaturePlans()) {
      try {
        plan.project(context);
      } catch (RuntimeException e) {
        context.addError(new com.ouroboros.exception.BusinessException("特性投影失败：" + plan.getFeatureId()
            + " / " + sourceModel.getFullName(), e));
      }
    }
  }
}
