package com.ouroboros.businessmodel.json;

import com.ouroboros.businessmodel.BusinessModelTypes;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;

public class CompositeViewBusinessModelMetaTypeProvider implements BusinessModelMetaTypeProvider {
  @Override
  public Class<?> getBusinessModelMetaType(String type) {
    if (BusinessModelTypes.COMPOSITE_VIEW.equalsIgnoreCase(type)) {
      return CompositeViewBusinessModelMeta.class;
    }
    return null;
  }
}
