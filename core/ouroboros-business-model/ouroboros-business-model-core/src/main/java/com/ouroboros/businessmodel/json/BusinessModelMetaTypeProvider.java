package com.ouroboros.businessmodel.json;

public interface BusinessModelMetaTypeProvider {
  Class<?> getBusinessModelMetaType(String type);
}
