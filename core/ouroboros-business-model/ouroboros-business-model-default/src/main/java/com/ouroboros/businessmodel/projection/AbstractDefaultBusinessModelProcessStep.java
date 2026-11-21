package com.ouroboros.businessmodel.projection;

import com.ouroboros.businessmodel.DefaultBusinessModel;

public abstract class AbstractDefaultBusinessModelProcessStep implements ModelProcessStep<DefaultBusinessModel> {
  @Override
  public Boolean support(ModelBundleProjector<?> projector) {
    return projector instanceof DefaultModelBundleProjector;
  }
}
