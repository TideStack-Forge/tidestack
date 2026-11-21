package com.ouroboros.logicflow.authoring.analysis;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.model.*;

class LogicFlowDataflowAnalyzerTest {
  private final ExpressionTypeQuery paths = (expression, bindings) -> {
    var segments = expression.split("\\.");
    var shape = bindings.getOrDefault(segments[0], TypeShape.unknown("unbound"));
    for (int i = 1; i < segments.length; i++) shape = shape.property(segments[i]);
    return shape;
  };

  @Test
  void mappingOutputBecomesScriptInputsAndLiteralArraysStayLiteral() {
    var mapping = node("map", "mapping").extraProp("mapping", Map.of("customerName", "$0.customer.name", "literal", List.of("$0.customer"))).build();
    var script = node("script", "script").inputPort("orderView").build();
    var flow = FlowModelMeta.builder().name("test").entryNode(mapping).node(script).connection(connection("map", "script")).build();
    var input = TypeShape.object(Map.of("customer", TypeShape.object(Map.of("name", TypeShape.scalar("string")), "map")), "map");
    var result = new LogicFlowDataflowAnalyzer().analyze(flow, List.of(input), Map.of(), paths);
    var scope = result.nodes().get("script").codeScopes().get("/script").bindings();
    assertEquals("string", scope.get("$0").property("customerName").type());
    assertEquals(scope.get("$0"), scope.get("$orderView"));
    assertEquals("string", scope.get("customerName").type());
    assertEquals("string", scope.get("$0").property("literal").element().type());
  }

  @Test
  void eachMappingAndFilterUseTheirActualItemScopes() {
    var item = TypeShape.object(Map.of("name", TypeShape.scalar("string")), "map");
    var mapping = node("map", "mapping").extraProp("mappingEachItem", true).extraProp("mapping", Map.of("label", "$item.name")).build();
    var filter = node("filter", "filter").extraProp("filterExpr", "name != null").build();
    var analyzer = new LogicFlowDataflowAnalyzer();
    var mapped = analyzer.analyze(FlowModelMeta.builder().entryNode(mapping).build(), List.of(TypeShape.array(item)), Map.of(), paths).nodes().get("map");
    assertEquals("string", mapped.outputs().get(0).shape().element().property("label").type());
    assertTrue(mapped.codeScopes().get("/mapping/label").bindings().containsKey("$item"));
    var filtered = analyzer.analyze(FlowModelMeta.builder().entryNode(filter).build(), List.of(TypeShape.array(item)), Map.of(), paths).nodes().get("filter");
    assertFalse(filtered.codeScopes().get("/filterExpr").bindings().containsKey("$item"));
    assertEquals(item, filtered.outputs().get(0).shape().element());
  }

  @Test
  void customAnalyzerUsesTheSameDispatchAndCyclesReturnPartial() {
    FlowNodeAnalyzer custom = new FlowNodeAnalyzer() {
      public Set<String> nodeTypes() { return Set.of("extension"); }
      public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) {
        return NodeAnalysis.output(TypeShape.object(Map.of("custom", TypeShape.scalar("boolean")), "map"));
      }
    };
    var upstream = node("ext", "extension").build();
    var downstream = node("script", "script").build();
    var flow = FlowModelMeta.builder().node(upstream).node(downstream).connection(connection("ext", "script")).build();
    var analyzers = new java.util.ArrayList<>(LogicFlowDataflowAnalyzer.discoveredAnalyzers()); analyzers.add(custom);
    assertEquals("boolean", new LogicFlowDataflowAnalyzer(analyzers).analyze(flow, List.of(), Map.of(), paths).nodes().get("script").codeScopes().get("/script").bindings().get("$0").property("custom").type());
    var cyclic = flow.map(builder -> builder.connection(connection("script", "ext")));
    assertFalse(new LogicFlowDataflowAnalyzer(analyzers).analyze(cyclic, List.of(), Map.of(), paths).diagnostics().isEmpty());
    assertThrows(IllegalArgumentException.class, () -> new LogicFlowDataflowAnalyzer(List.of(custom, custom)));
  }

  @Test
  void disabledNodesAndAnalyzerFailuresProduceDiagnosticsAndUnknownShapes() {
    var disabled = node("disabled", "const").enabled(false).build();
    var script = node("script", "script").build();
    var flow = FlowModelMeta.builder().entryNode(disabled).node(script)
        .connection(connection("disabled", "script")).build();
    var result = new LogicFlowDataflowAnalyzer().analyze(flow, List.of(TypeShape.scalar("string")), Map.of(), paths);
    assertTrue(result.diagnostics().stream().anyMatch(item -> item.code().equals("disabled-node")));
    assertTrue(result.diagnostics().stream().anyMatch(item -> item.code().equals("disabled-connection")));

    FlowNodeAnalyzer broken = new FlowNodeAnalyzer() {
      public Set<String> nodeTypes() { return Set.of("broken"); }
      public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) { throw new IllegalStateException("bad extension"); }
    };
    var brokenResult = new LogicFlowDataflowAnalyzer(List.of(broken)).analyze(FlowModelMeta.builder().entryNode("bad")
        .node(node("bad", "broken").build()).build(), List.of(), Map.of(), paths);
    assertTrue(brokenResult.diagnostics().stream().anyMatch(item -> item.code().equals("analyzer-failure")));
  }

  @Test
  void structuralNodesFollowRuntimeMergeAndPickSemantics() {
    var first = TypeShape.object(Map.of("a", TypeShape.scalar("string"),
        "nested", TypeShape.object(Map.of("x", TypeShape.scalar("string")), "map")), "map");
    var second = TypeShape.object(Map.of("b", TypeShape.scalar("integer"),
        "nested", TypeShape.object(Map.of("y", TypeShape.scalar("boolean")), "map")), "map");
    var merge = node("merge", "merge").extraProp("deepMerge", true).build();
    var flow = FlowModelMeta.builder().entryNode("merge").node(merge).build();
    var output = new LogicFlowDataflowAnalyzer().analyze(flow, List.of(first, second), Map.of(), paths).nodes().get("merge").outputs().get(0).shape();
    assertEquals("string", output.property("nested").property("x").type());
    assertEquals("boolean", output.property("nested").property("y").type());

    var pick = node("pick", "pick").extraProp("key", "nested.x").build();
    var picked = new LogicFlowDataflowAnalyzer().analyze(FlowModelMeta.builder().entryNode("pick").node(pick).build(), List.of(first), Map.of(), paths)
        .nodes().get("pick").outputs().get(0).shape();
    assertEquals("string", picked.type());
  }

  @Test void switchPreservesPayloadOnConditionalPortsAndComparatorScopesUseElementTypes() {
    var order = TypeShape.object(Map.of("total", TypeShape.scalar("number")), "map");
    var decision = node("branch", "switch").extraProp("conditions", List.of(Map.of("expression", "total > 0"))).build();
    var branching = analyzeNode(decision, List.of(order));
    assertEquals(2, branching.outputs().size());
    assertEquals("conditional", branching.outputs().get(1).availability());
    assertEquals(order, branching.outputs().get(0).shape());
    assertEquals("boolean", branching.codeScopes().get("/conditions/0/expression").expectedResult().type());
    for (var type : List.of("concat", "distinct", "intersect", "subtract")) {
      var comparison = analyzeNode(node("compare", type).build(), List.of(TypeShape.array(order), TypeShape.array(order)));
      var scope = comparison.codeScopes().get("/isEqualExpr");
      assertEquals(order, scope.bindings().get("a"));
      assertEquals(order, scope.bindings().get("b"));
      assertFalse(scope.bindings().containsKey("$item"));
      assertEquals(order, comparison.outputs().get(0).shape().element());
    }
  }

  @Test void constantsAndStructuralTransformsProvideTypesToDownstreamNodes() {
    for (var type : List.of("string", "integer", "double", "boolean", "any")) {
      var output = analyzeNode(node("constant", "const").extraProp("dataType", type).extraProp("value", Map.of("name", "hello")).build(), List.of()).outputs().get(0).shape();
      assertEquals(switch (type) { case "double" -> "number"; case "any" -> "object"; default -> type; }, output.type());
    }
    var item = TypeShape.object(Map.of("name", TypeShape.scalar("string"), "secret", TypeShape.scalar("string")), "map");
    assertFalse(analyzeNode(node("omit", "omit").extraProp("omitFields", List.of("secret")).build(), List.of(item))
        .outputs().get(0).shape().properties().containsKey("secret"));
    assertEquals(item, analyzeNode(node("take", "take").build(), List.of(TypeShape.array(item))).outputs().get(0).shape());
    assertEquals("string", analyzeNode(node("pick", "pick").extraProp("key", "name").extraProp("pickEachItem", true).build(),
        List.of(TypeShape.array(item))).outputs().get(0).shape().element().type());
    assertTrue(analyzeNode(node("invalid", "merge").build(), List.of(TypeShape.scalar("string"))).outputs().get(0).shape().isUnknown());
    var validated = analyzeNode(node("validate", "validate").extraProp("schema", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "integer")))).build(), List.of(item));
    assertEquals("integer", validated.outputs().get(0).shape().property("id").type());
  }

  @Test void mappingExpansionConflictPoliciesAndNestedLiteralKeysFollowRuntime() {
    var item = TypeShape.object(Map.of("name", TypeShape.scalar("string")), "map");
    for (var strategy : List.of("OVERWRITE", "SKIP", "ERROR", "MERGE")) {
      var rules = new java.util.LinkedHashMap<String, Object>();
      rules.put("&", "$0"); rules.put("name", 42); rules.put("nested", Map.of("literal.key", true));
      var result = analyzeNode(node("mapping", "mapping").extraProp("mapping", rules).extraProp("conflictStrategy", strategy).build(), List.of(item));
      var output = result.outputs().get(0).shape();
      assertEquals("boolean", output.property("nested").property("literal.key").type());
      if (strategy.equals("SKIP")) assertEquals("string", output.property("name").type());
      else if (strategy.equals("ERROR")) assertTrue(result.diagnostics().stream().anyMatch(issue -> issue.code().equals("mapping-conflict")));
      else assertEquals("integer", output.property("name").type());
    }
    var open = analyzeNode(node("mapping", "mapping").extraProp("mapping", Map.of("&", "$missing")).build(), List.of(item));
    assertTrue(open.diagnostics().stream().anyMatch(issue -> issue.code().equals("unknown-expansion")));
  }

  private NodeAnalysis analyzeNode(FlowNodeMeta node, List<TypeShape> inputs) {
    return new LogicFlowDataflowAnalyzer().analyze(FlowModelMeta.builder().entryNode(node).build(), inputs, Map.of(), paths).nodes().get(node.getName());
  }

  @Test void declaredScriptOutputProvidesDownstreamFieldsWithoutExecutingSource() {
    var producer = node("producer", "script").extraProp("script", "throw new Error('must not execute')")
        .extraProp("outputSchema", Map.of("type", "object", "properties", Map.of("approved", Map.of("type", "boolean")))).build();
    var consumer = node("consumer", "script").build();
    var flow = FlowModelMeta.builder().entryNode(producer).node(consumer).connection(connection("producer", "consumer")).build();
    var result = new LogicFlowDataflowAnalyzer().analyze(flow, List.of(), Map.of(), paths);
    assertEquals("boolean", result.nodes().get("consumer").codeScopes().get("/script").bindings().get("approved").type());
    assertEquals("declared", result.nodes().get("producer").outputs().get(0).shape().schema().path("x-evidence").asText());
  }

  private FlowNodeMeta.Builder node(String name, String type) { return FlowNodeMeta.builder().name(name).type(type); }
  private FlowConnectionMeta connection(String from, String to) { return FlowConnectionMeta.builder().sourceNode(from).sourceOutputPort(0).targetNode(to).targetInputPort(0).build(); }
}
