package com.ouroboros.logicflow.authoring.analysis;

import com.ouroboros.codeintelligence.type.TypeShape;

/** Read-only declared resource signatures. Node analyzers decide how each signature is used. */
@FunctionalInterface
public interface FlowTypeContracts {
  TypeShape find(String resourceType, String name, String role);
  default java.util.Optional<com.fasterxml.jackson.databind.JsonNode> metadata(String resourceType, String name) { return java.util.Optional.empty(); }
  default TypeShape callFlow(String name, java.util.List<TypeShape> inputs, java.util.Map<String, TypeShape> globals,
      com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery expressions) { return find("logic-flow", name, "output"); }
  default TypeShape scriptOutput(String language, String source, java.util.Map<String, TypeShape> bindings) {
    return TypeShape.unknown("script-output-not-analyzed");
  }
  static FlowTypeContracts empty() { return (type, name, role) -> TypeShape.unknown("missing-contract:" + type + ":" + name + ":" + role); }
}
