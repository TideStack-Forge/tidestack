package com.ouroboros.businessmodel.json;

import com.ouroboros.businessmodel.BusinessModelTypes;
import com.ouroboros.businessmodel.meta.DashboardBusinessModelMeta;

public class DashboardBusinessModelMetaTypeProvider implements BusinessModelMetaTypeProvider {
  @Override
  public Class<?> getBusinessModelMetaType(String type) {
    if (BusinessModelTypes.DASHBOARD.equalsIgnoreCase(type)) {
      return DashboardBusinessModelMeta.class;
    }
    return null;
  }
}
