package com.ouroboros.logicflow.dev.scriptintelligence;

import java.util.Map;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.expression.intelligence.SpelTypeQuery;
import com.ouroboros.logicflow.authoring.analysis.LogicFlowDataflowAnalyzer;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.util.Json;

/** Finds statically known RPC call inputs across the application's published flow metadata. */
public final class LogicFlowInvocationInputProvider implements InvocationInputProvider {
  public java.util.Optional<TypeShape> infer(String resourceType, String name, ReadOnlyMetadataView metadata) {
    if (!"rpc-model".equals(resourceType)) return java.util.Optional.empty();
    TypeShape result = null;
    int remaining = 4000;
    var globals = new java.util.LinkedHashMap<String, TypeShape>();
    metadata.environment().getGlobals().forEach(binding -> { if (binding.getShape() != null) globals.put(binding.getName(), binding.getShape()); });
    for (var item : metadata.application().getModule("logic-flow")) {
      if (item.getDetails() == null) continue;
      var flow = Json.toBean(FlowModelMeta.class, Json.toJsonString(item.getDetails()));
      if (Boolean.FALSE.equals(flow.isEnabled())) continue;
      var calls = flow.getNodes().stream().filter(node -> !Boolean.FALSE.equals(node.isEnabled()) && "callrpc".equalsIgnoreCase(node.getType())
          && name.equalsIgnoreCase(node.getExtraProp(String.class, "modelName").orElse(""))).toList();
      if (calls.isEmpty()) continue;
      remaining -= flow.getNodes().size();
      if (remaining < 0) return java.util.Optional.of(TypeShape.unknown("invocation-analysis-budget"));
      var analysis = new LogicFlowDataflowAnalyzer().analyze(flow, LogicFlowCodeContextProvider.entryInputs(flow), globals,
          new SpelTypeQuery(), new MetadataFlowTypeContracts(metadata.application()));
      for (var call : calls) {
        if (!analysis.reachableNodes().contains(call.getName())) continue;
        var inputs = analysis.nodes().get(call.getName()).inputs();
        var shape = inputs.isEmpty() ? TypeShape.unknown("call-input-unavailable") : inputs.getFirst().shape();
        result = result == null ? shape : result.merge(shape);
      }
    }
    return result == null ? java.util.Optional.empty() : java.util.Optional.of(result.annotated("x-evidence", "static-call-sites"));
  }
}
