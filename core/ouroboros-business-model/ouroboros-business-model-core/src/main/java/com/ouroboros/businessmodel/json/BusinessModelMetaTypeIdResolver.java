package com.ouroboros.businessmodel.json;

import java.io.IOException;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.util.SPI;

public class BusinessModelMetaTypeIdResolver extends TypeIdResolverBase {
  private JavaType superType;

  @Override
  public void init(JavaType bt) {
    superType = bt;
  }

  @Override
  public String idFromValue(Object o) {
    return null;
  }

  @Override
  public String idFromValueAndType(Object o, Class<?> aClass) {
    return null;
  }

  @Override
  public JsonTypeInfo.Id getMechanism() {
    return JsonTypeInfo.Id.CUSTOM;
  }

  @Override
  public JavaType typeFromId(DatabindContext context, String id) throws IOException {
    Class<?> subType = SPI.getCachedServiceStream(BusinessModelMetaTypeProvider.class)
        .map(provider -> provider.getBusinessModelMetaType(id))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse((Class) BusinessModelMeta.class);
    return context.constructSpecializedType(superType, subType);
  }
}
