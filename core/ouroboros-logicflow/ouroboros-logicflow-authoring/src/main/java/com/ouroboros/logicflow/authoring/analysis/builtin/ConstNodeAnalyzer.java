package com.ouroboros.logicflow.authoring.analysis.builtin;
import java.util.Set;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.authoring.analysis.*;
public final class ConstNodeAnalyzer implements FlowNodeAnalyzer {
  public Set<String> nodeTypes() { return Set.of("const"); }
  public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) {
    var value = input.node().getExtraProp("value").orElse(null);
    var type = input.node().getExtraProp(String.class, "dataType").orElse("any").toLowerCase(java.util.Locale.ROOT);
    var shape = switch (type) {
      case "string", "date", "datetime", "time" -> TypeShape.scalar("string");
      case "integer", "long" -> TypeShape.scalar("integer");
      case "float", "double" -> TypeShape.scalar("number");
      case "boolean" -> TypeShape.scalar("boolean");
      default -> TypeShape.literal(value);
    };
    return NodeAnalysis.output(shape);
  }
}
