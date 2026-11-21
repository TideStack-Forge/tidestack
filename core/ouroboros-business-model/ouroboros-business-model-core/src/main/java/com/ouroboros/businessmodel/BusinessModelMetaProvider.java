package com.ouroboros.businessmodel;

import java.util.Map;
import java.util.Optional;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;

public interface BusinessModelMetaProvider {
  Optional<BusinessModelMeta> getBusinessModelMeta(String fullName);

  Map<String, BusinessModelMeta> getBusinessModelMetaMap();
}
