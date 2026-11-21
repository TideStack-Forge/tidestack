package com.ouroboros.logicflow.authoring.analysis;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.expression.intelligence.SpelTypeQuery;
import com.ouroboros.logicflow.model.*;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;

class MappingSemanticsTest {
  @Test void expansionPathsAndConflictStrategiesMatchExecution() {
    var input = Map.of("name", "old", "profile", Map.of("active", true));
    for (String strategy : List.of("OVERWRITE", "SKIP", "MERGE", "ERROR")) {
      var rules = new LinkedHashMap<String, Object>();
      rules.put("&", "$0"); rules.put("name", 42); rules.put("nested.&", "$0.profile");
      rules.put("lines[0].value", 3); rules.put("literal", List.of("$0.name"));
      var flow = flow(rules, strategy, false);
      var inferred = infer(flow, input);
      var execution = FlowModelFactory.build(flow, NodeReferenceCatalog.empty()).execute(input);
      if (strategy.equals("ERROR")) {
        assertTrue(execution.isFailure());
        assertTrue(inferred.diagnostics().stream().anyMatch(issue -> issue.code().equals("mapping-conflict")));
      } else {
        assertTrue(execution.isSuccess(), () -> execution.getCause().toString());
        assertCompatible(inferred.nodes().get("map").outputs().get(0).shape(), execution.get());
      }
    }
  }

  @Test void nestedMapsMergeNullOverwriteAndItemScopesMatchExecution() {
    var items = List.of(Map.of("label", "one", "nested", Map.of("old", 1)), Map.of("label", "two", "nested", Map.of("old", 2)));
    var rules = new LinkedHashMap<String, Object>();
    rules.put("&", "$item"); rules.put("nested", Map.of("new", true)); rules.put("index", "$index");
    rules.put("renamed", "$item.label");
    var flow = flow(rules, "MERGE", true);
    var execution = FlowModelFactory.build(flow, NodeReferenceCatalog.empty()).execute(items);
    assertTrue(execution.isSuccess(), () -> execution.getCause().toString());
    assertCompatible(infer(flow, items).nodes().get("map").outputs().get(0).shape(), execution.get());
    var nullRules = new LinkedHashMap<String, Object>(); nullRules.put("&", "$0"); nullRules.put("name", "'filled'");
    var nullInput = new LinkedHashMap<String, Object>(); nullInput.put("name", null);
    var nullFlow = flow(nullRules, "SKIP", false);
    assertCompatible(infer(nullFlow, nullInput).nodes().get("map").outputs().get(0).shape(), FlowModelFactory.build(nullFlow, NodeReferenceCatalog.empty()).execute(nullInput).get());
  }

  private FlowModelMeta flow(Map<String, Object> rules, String strategy, boolean each) {
    return FlowModelMeta.builder().name("mapping-contract").enabled(true).exitNode("map")
        .entryNode(FlowNodeMeta.builder().name("map").type("mapping").inputPort("input")
            .extraProp("mapping", rules).extraProp("conflictStrategy", strategy).extraProp("mappingEachItem", each).build()).build();
  }
  private LogicFlowDataflowAnalysis infer(FlowModelMeta flow, Object input) {
    return new LogicFlowDataflowAnalyzer().analyze(flow, List.of(TypeShape.literal(input)), Map.of(), new SpelTypeQuery());
  }
  private void assertCompatible(TypeShape predicted, Object value) {
    var actual = TypeShape.literal(value);
    assertFalse(predicted.isUnknown(), () -> predicted.schema().toString());
    assertEquals(actual.type(), predicted.type());
    if (value instanceof Map<?, ?> map) {
      assertEquals(actual.properties().keySet(), predicted.properties().keySet());
      map.forEach((key, item) -> assertCompatible(predicted.property(String.valueOf(key)), item));
    } else if (value instanceof Collection<?> list) list.forEach(item -> assertCompatible(predicted.element(), item));
  }
}
