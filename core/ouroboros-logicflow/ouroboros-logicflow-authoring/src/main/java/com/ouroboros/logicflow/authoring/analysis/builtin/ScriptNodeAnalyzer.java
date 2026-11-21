package com.ouroboros.logicflow.authoring.analysis.builtin;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.authoring.analysis.*;
public final class ScriptNodeAnalyzer implements FlowNodeAnalyzer {
  public Set<String> nodeTypes() { return Set.of("script"); }
  public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) {
    var bindings = NodeScopes.ordinary(input);
    var script = input.node().getExtraProp(String.class, "script").orElse("");
    var language = input.node().getExtraProp(String.class, "language").orElse("groovy");
    var output = script.isBlank() ? bindings.getOrDefault("$0", TypeShape.unknown("no-input")) : TypeShape.unknown("dynamic-script-output");
    var declared = input.node().getExtraProp("outputSchema");
    if (declared.isPresent() && !new ObjectMapper().valueToTree(declared.get()).isEmpty()) {
      output = TypeShape.mapSchema(new ObjectMapper().valueToTree(declared.get())).annotated("x-evidence", "declared");
    } else if (!script.isBlank()) {
      output = input.contracts().scriptOutput(language, script, bindings);
    }
    var pointer = input.node().getExtraProp(String.class, "codeSourcePointer").orElse("/script");
    var scopes = new java.util.LinkedHashMap<String, CodeScope>();
    scopes.put("/script", new CodeScope(language, bindings, output));
    scopes.put(pointer, new CodeScope(language, bindings, output));
    var diagnostics = output.isUnknown() ? List.of(new AnalysisDiagnostic(input.node().getName(), pointer,
        "unknown-script-output", output.schema().path("x-unknownReason").asText())) : List.<AnalysisDiagnostic>of();
    return new NodeAnalysis(Map.of(0, PortShape.always(output)), scopes, diagnostics);
  }
}
