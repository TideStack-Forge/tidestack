package com.ouroboros.businessmodel;

import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.logicflow.model.FlowModelMeta;

/** Document editing capability supplied by the model backend. */
public interface BusinessModelScriptAction {
  record Plan(DefaultBusinessModelMeta model, FlowModelMeta flow) { }

  Plan prepare(DefaultBusinessModelMeta original, BusinessModelProvider provider, String functionName,
      String actionName, String language, String script, Boolean readOnly);

  FlowModelMeta scriptFlow(String flowName, String modelFullName, BusinessModel model, String language, String script);

  static java.util.Map<String, Object> inputSchema() {
    return java.util.Map.of("type", "object", "properties", java.util.Map.of("params", java.util.Map.of("type", "object"), "data", java.util.Map.of()), "additionalProperties", false);
  }
}
