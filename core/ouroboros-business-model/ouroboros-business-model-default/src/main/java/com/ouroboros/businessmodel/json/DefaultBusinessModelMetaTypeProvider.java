package com.ouroboros.businessmodel.json;

import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;

public class DefaultBusinessModelMetaTypeProvider implements BusinessModelMetaTypeProvider {
  @Override
  public Class<?> getBusinessModelMetaType(String type) {
    if ("default".equalsIgnoreCase(type)) {
      return DefaultBusinessModelMeta.class;
    }
    return null;
  }
}
