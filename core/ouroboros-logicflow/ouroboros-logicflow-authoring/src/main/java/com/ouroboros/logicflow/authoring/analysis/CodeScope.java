package com.ouroboros.logicflow.authoring.analysis;
import java.util.Map;
import com.ouroboros.codeintelligence.type.TypeShape;
public record CodeScope(String profile, Map<String, TypeShape> bindings, TypeShape expectedResult) {
  public CodeScope { bindings = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(bindings)); }
}
