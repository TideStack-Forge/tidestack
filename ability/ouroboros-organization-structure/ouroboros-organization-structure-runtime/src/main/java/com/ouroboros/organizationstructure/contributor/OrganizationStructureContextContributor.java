package com.ouroboros.organizationstructure.contributor;

import java.util.HashMap;
import java.util.Map;

import com.ouroboros.expression.ExpressionContextContributor;
import com.ouroboros.script.ScriptContextContributor;

public class OrganizationStructureContextContributor implements ExpressionContextContributor, ScriptContextContributor {
  static Map<String, Object> bindings = new HashMap<>();
  static OrganizationScriptContext organizationContext = new OrganizationScriptContext();

  static {
    bindings.put("OrganizationHolder", organizationContext);
    bindings.put("OrganizationContext", organizationContext);
  }

  @Override
  public Map<String, Object> getBindings() {
    return bindings;
  }
}
