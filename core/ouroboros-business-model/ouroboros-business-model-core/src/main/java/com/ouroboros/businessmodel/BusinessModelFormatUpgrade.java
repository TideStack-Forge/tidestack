package com.ouroboros.businessmodel;

import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.logicflow.model.FlowModelMeta;

/** Document editing capability supplied by the model backend. */
public interface BusinessModelFormatUpgrade {
  record Plan(DefaultBusinessModelMeta model, java.util.List<FlowModelMeta> scriptFlows) {
    public Plan { scriptFlows = java.util.List.copyOf(scriptFlows); }
  }

  Plan prepare(DefaultBusinessModelMeta original, BusinessModelProvider provider);
}
