package com.ouroboros.logicflow.authoring.analysis;
import java.util.List;
import java.util.Map;
import com.ouroboros.codeintelligence.type.TypeShape;
public record NodeAnalysis(Map<Integer, PortShape> outputs, Map<String, CodeScope> codeScopes, List<AnalysisDiagnostic> diagnostics, List<PortShape> inputs) {
  public NodeAnalysis(Map<Integer, PortShape> outputs, Map<String, CodeScope> codeScopes, List<AnalysisDiagnostic> diagnostics) { this(outputs, codeScopes, diagnostics, List.of()); }
  public NodeAnalysis { outputs = Map.copyOf(outputs); codeScopes = Map.copyOf(codeScopes); diagnostics = List.copyOf(diagnostics); inputs = List.copyOf(inputs); }
  public static NodeAnalysis output(TypeShape shape) { return new NodeAnalysis(Map.of(0, PortShape.always(shape)), Map.of(), List.of()); }
}
