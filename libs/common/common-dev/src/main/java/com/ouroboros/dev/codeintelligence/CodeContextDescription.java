package com.ouroboros.dev.codeintelligence;
import java.util.List;
import java.util.Map;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.script.intelligence.ScriptIntelligenceGlobal;
public record CodeContextDescription(String profile, List<ScriptIntelligenceGlobal> bindings, TypeShape expectedResult,
    List<Map<String, Object>> diagnostics, boolean allowGlobals, Map<String, Object> projectionHints) {
  public CodeContextDescription(String profile, List<ScriptIntelligenceGlobal> bindings, TypeShape expectedResult,
      List<Map<String, Object>> diagnostics, boolean allowGlobals) {
    this(profile, bindings, expectedResult, diagnostics, allowGlobals, Map.of());
  }
  public CodeContextDescription {
    bindings = List.copyOf(bindings);
    diagnostics = List.copyOf(diagnostics);
    projectionHints = Map.copyOf(projectionHints);
  }
}
