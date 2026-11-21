package com.ouroboros.logicflow.authoring.analysis.builtin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.authoring.analysis.*;
public final class SwitchNodeAnalyzer implements FlowNodeAnalyzer {
  public Set<String> nodeTypes() { return Set.of("switch"); }
  public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) {
    var conditions = input.node().getExtraProp(List.class, "conditions").orElse(List.of());
    var outputs = new LinkedHashMap<Integer, PortShape>();
    var scopes = new LinkedHashMap<String, CodeScope>();
    for (int i = 0; i <= conditions.size(); i++) {
      outputs.put(i, new PortShape(input.firstInput(), "conditional"));
      if (i < conditions.size()) scopes.put("/conditions/" + i + "/expression", new CodeScope("spel", NodeScopes.ordinary(input), TypeShape.scalar("boolean")));
    }
    return new NodeAnalysis(outputs, scopes, List.of());
  }
}
