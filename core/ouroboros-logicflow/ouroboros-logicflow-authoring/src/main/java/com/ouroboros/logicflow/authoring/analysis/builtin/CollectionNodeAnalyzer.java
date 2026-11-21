package com.ouroboros.logicflow.authoring.analysis.builtin;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.authoring.analysis.*;
public final class CollectionNodeAnalyzer implements FlowNodeAnalyzer {
  public Set<String> nodeTypes() { return Set.of("concat", "distinct", "intersect", "subtract"); }
  public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) {
    var first = input.firstInput().element();
    var all = input.inputs().stream().map(port -> "array".equals(port.shape().type()) ? port.shape().element() : port.shape()).reduce(TypeShape::merge).orElse(first);
    var scope = new java.util.LinkedHashMap<>(input.globals());
    var left = "concat".equals(input.node().getType()) || "distinct".equals(input.node().getType()) ? all : first;
    var right = input.inputs().size() > 1 ? input.inputs().get(1).shape().element() : left;
    scope.putAll(Map.of("a", left, "b", right, "$0", left, "$1", right));
    scope.putAll(input.globals());
    var type = input.node().getType().toLowerCase(java.util.Locale.ROOT);
    var output = "concat".equals(type) ? TypeShape.array(all)
        : "array".equals(input.firstInput().type()) ? input.firstInput() : TypeShape.array(first);
    return new NodeAnalysis(Map.of(0, PortShape.always(output)), Map.of("/isEqualExpr", new CodeScope("spel", scope, TypeShape.scalar("boolean"))), List.of());
  }
}
