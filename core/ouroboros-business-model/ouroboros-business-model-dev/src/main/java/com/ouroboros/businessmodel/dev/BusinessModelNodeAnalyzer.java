package com.ouroboros.businessmodel.dev;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.authoring.analysis.*;

/** Business node contracts stay with the module that owns their runtime semantics. */
public final class BusinessModelNodeAnalyzer implements FlowNodeAnalyzer {
  public Set<String> nodeTypes() { return Set.of("syncChildCollections", "emitBusinessEvent"); }

  public NodeAnalysis analyze(NodeAnalysisInput input, ExpressionTypeQuery expressions) {
    if ("syncChildCollections".equalsIgnoreCase(input.node().getType())) {
      var plans = new ObjectMapper().valueToTree(input.node().getExtraProp("plans").orElse(java.util.List.of()));
      return NodeAnalysis.output(children(plans, input.contracts(), 0));
    }
    var payload = input.firstInput().property("payload");
    var modelName = input.node().getExtraProp(String.class, "modelFullName").orElse("");
    var eventName = input.node().getExtraProp(String.class, "eventName").orElse("");
    var model = input.contracts().metadata("business-model", modelName);
    if (model.isPresent()) {
      for (var event : model.get().path("events")) {
        if (eventName.equals(event.path("name").asText()) && event.path("payloadSchema").isObject()
            && !event.path("payloadSchema").isEmpty()) payload = TypeShape.mapSchema(event.path("payloadSchema"));
      }
    }
    return NodeAnalysis.output(BusinessEventTypeShapes.envelope(payload, input.firstInput().property("subjectId")));
  }

  private TypeShape children(JsonNode plans, FlowTypeContracts contracts, int depth) {
    if (depth > 24) return TypeShape.unknown("child-collection-depth-budget");
    var relations = new LinkedHashMap<String, TypeShape>();
    for (var plan : plans) {
      var record = contracts.find("data-model", plan.path("childDataModelName").asText(), "record");
      if (plan.path("children").isArray() && !plan.path("children").isEmpty()) {
        var fields = new LinkedHashMap<>(record.properties());
        fields.putAll(children(plan.path("children"), contracts, depth + 1).properties());
        var schema = (ObjectNode) TypeShape.object(fields, "map").schema();
        // Unknown record fields remain open while nested relations can still be described.
        schema.put("additionalProperties", record.isUnknown() || record.schema().path("additionalProperties").asBoolean());
        record = new TypeShape(schema);
      }
      relations.put(plan.path("relationName").asText(), TypeShape.array(record));
    }
    var schema = (ObjectNode) TypeShape.object(relations, "map").schema();
    schema.putArray("required"); // Missing submitted collections do not create keys in the synchronization result.
    return new TypeShape(schema);
  }
}
