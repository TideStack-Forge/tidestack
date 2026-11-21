package com.ouroboros.dev.codeintelligence;

import java.util.Map;
import com.ouroboros.codeintelligence.type.TypeShape;

/** Immutable source and binding contract; application environment belongs to the orchestration boundary. */
public record ScriptAnalysisQuery(String language, String source, Map<String, TypeShape> bindings) {
  public ScriptAnalysisQuery { bindings = Map.copyOf(bindings); }
  public String key() { return com.ouroboros.codeintelligence.runtime.ContentVersion.of(com.ouroboros.util.Json.toJsonString(this)); }
}
