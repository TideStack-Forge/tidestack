package com.ouroboros.logicflow.authoring.analysis;
import java.util.Map;
import java.util.List;
import java.util.Set;
public record LogicFlowDataflowAnalysis(Map<String, NodeAnalysis> nodes, List<AnalysisDiagnostic> diagnostics,
    Set<String> reachableNodes, Set<String> successfulTerminals) {
  public LogicFlowDataflowAnalysis {
    nodes = Map.copyOf(nodes); diagnostics = List.copyOf(diagnostics);
    reachableNodes = Set.copyOf(reachableNodes); successfulTerminals = Set.copyOf(successfulTerminals);
  }
}
