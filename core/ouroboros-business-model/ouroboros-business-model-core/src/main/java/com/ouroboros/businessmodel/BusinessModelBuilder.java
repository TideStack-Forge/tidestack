package com.ouroboros.businessmodel;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;

public interface BusinessModelBuilder<M extends BusinessModelMeta, B extends BusinessModel> {
  Boolean support(BusinessModelMeta businessModelMeta);

  B build(M businessModelMeta, BusinessModelProvider businessModelProvider);
}
