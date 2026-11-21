package com.ouroboros.logicflow.authoring.analysis.builtin;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.authoring.analysis.*;
public final class FilterNodeAnalyzer implements FlowNodeAnalyzer {
  public Set<String> nodeTypes() { return Set.of("filter"); }
  public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) {
    var diagnostics = "array".equals(input.firstInput().type()) || input.firstInput().isUnknown() ? List.<AnalysisDiagnostic>of()
        : List.of(new AnalysisDiagnostic(input.node().getName(), "/filterExpr", "collection-required", "Filter input must be a collection"));
    return new NodeAnalysis(Map.of(0, PortShape.always(input.firstInput())),
        Map.of("/filterExpr", new CodeScope("spel", NodeScopes.item(input, false), TypeShape.scalar("boolean"))), diagnostics);
  }
}
