package com.ouroboros.logicflow.dev.scriptintelligence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.dev.metadata.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.util.Json;

class LogicFlowCodeContextProviderTest {
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"javascript", "groovy", "fixture-language"})
  void declaredInputSchemaPropagatesThroughMappingIntoScriptAndRejectsWrongSites(String language) {
    var draft = Json.toBean(JsonNode.class, """
        {"name":"flow","entryNode":"mapping","inputSchema":{"type":"object","properties":{"customer":{"type":"object","properties":{"name":{"type":"string"}}}}},
          "nodes":[{"name":"mapping","type":"mapping","mapping":{"label":"$0.customer.name"}},
          {"name":"script","type":"script","language":"javascript","script":"","inputPorts":[{"name":"mapped"}]}],
          "connections":[{"sourceNode":"mapping","sourceOutputPort":0,"targetNode":"script","targetInputPort":0}]}
        """);
    ((com.fasterxml.jackson.databind.node.ObjectNode) draft.path("nodes").get(1)).put("language", language);
    var request = new CodeContextRequest("flow-editor", 3, 1, "logicflow.script", language, new CodeContextRequest.Resource("logicflow", "flow"),
        new CodeContextRequest.Location("script", "/script"), draft);
    var provider = new LogicFlowCodeContextProvider();
    var view = new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.unavailable(), new ScriptIntelligenceSnapshot());
    var result = provider.describe(request, view);
    assertEquals("string", result.bindings().stream().filter(item -> "$mapped".equals(item.getName())).findFirst().orElseThrow().getShape().property("label").type());
    var invalid = new CodeContextRequest("flow-editor", 3, 1, "logicflow.filter", language, request.resource(), request.location(), draft);
    assertThrows(BusinessException.class, () -> provider.describe(invalid, view));
  }
  @Test void resourceContractsRespectDraftPrecedenceAndJavaRepresentations() {
    var record = TypeShape.object(Map.of("id", TypeShape.scalar("integer")), "map");
    var model = new Metadata(); model.setName("Order"); model.setDetails(Map.of("recordSchema", record.schema(),
        "instanceSchema", TypeShape.object(Map.of("fullName", TypeShape.scalar("string")), "java").schema()));
    var snapshot = ApplicationMetadataSnapshot.current(Map.of("data-model", List.of(model)));
    var contracts = new MetadataFlowTypeContracts(snapshot);
    assertEquals("integer", contracts.find("data-model", "Order", "record").property("id").type());
    assertEquals("java", contracts.find("data-model", "Order", "instance").representation());
    var draft = Json.toBean(JsonNode.class, "{\"fields\":[{\"name\":\"newField\",\"type\":\"custom\"}]}");
    var changed = new MetadataFlowTypeContracts(snapshot, Map.of("data-model", Map.of("Order", draft)));
    ((com.fasterxml.jackson.databind.node.ObjectNode) draft).removeAll();
    assertTrue(changed.find("data-model", "Order", "record").properties().containsKey("newField"));
    assertFalse(changed.find("data-model", "Order", "record").properties().containsKey("id"));
  }

  @Test void validationSchemaSuppliesNestedAndArrayInputsToDownstreamScript() {
    var result = scriptContext("""
        {"name":"flow","entryNode":"validate","nodes":[
          {"name":"validate","type":"validate","schema":{"type":"object","required":["customer"],"properties":{
            "customer":{"type":"object","properties":{"name":{"type":"string"}}},
            "items":{"type":"array","items":{"type":"object","properties":{"quantity":{"type":"integer"}}}}}}},
          {"name":"script","type":"script","language":"javascript","script":"throw new Error('must not execute')",
            "inputPorts":[{"name":"validated"}]}],
          "connections":[{"sourceNode":"validate","sourceOutputPort":0,"targetNode":"script","targetInputPort":0}]}
        """);

    var input = binding(result, "$0");
    assertEquals("string", input.property("customer").property("name").type());
    assertEquals("integer", input.property("items").element().property("quantity").type());
    assertEquals(java.util.Set.of("customer"), input.required());
    assertEquals(input, binding(result, "$validated"));
    assertEquals(input.property("customer"), binding(result, "customer"));
    assertEquals("validated", input.schema().path("x-evidence").asText());
    assertEquals(List.of("unknown-script-output"), result.diagnostics().stream().map(issue -> issue.get("code")).toList());
  }

  @Test void validationThenMappingReconstructsScriptInputWithRealSpelInference() {
    var result = scriptContext("""
        {"name":"flow","entryNode":"validate","nodes":[
          {"name":"validate","type":"validate","schema":{"type":"object","properties":{
            "customer":{"type":"object","properties":{"name":{"type":"string"}}},
            "items":{"type":"array","items":{"type":"object","properties":{"quantity":{"type":"integer"}}}}}}},
          {"name":"map","type":"mapping","mapping":{
            "displayName":"$0.customer.name + '!'","firstQuantity":"$0.items[0].quantity",
            "profile.active":true,"literal":["$0.customer.name"]}},
          {"name":"script","type":"script","language":"javascript","script":"","inputPorts":[{"name":"mapped"}]}],
          "connections":[
            {"sourceNode":"validate","sourceOutputPort":0,"targetNode":"map","targetInputPort":0},
            {"sourceNode":"map","sourceOutputPort":0,"targetNode":"script","targetInputPort":0}]}
        """);

    var input = binding(result, "$0");
    assertEquals("string", input.property("displayName").type());
    assertEquals("integer", input.property("firstQuantity").type());
    assertEquals("boolean", input.property("profile").property("active").type());
    assertEquals("string", input.property("literal").element().type());
    assertFalse(input.properties().containsKey("customer"));
    assertEquals(input, binding(result, "$mapped"));
    assertEquals(input.property("displayName"), binding(result, "displayName"));
    assertTrue(result.diagnostics().isEmpty());
  }

  @Test void emptyValidationSchemaPreservesMappedInputsToDownstreamScript() {
    var result = scriptContext("""
        {"name":"flow","entryNode":"map","inputSchema":{"type":"object","properties":{"id":{"type":"integer"}}},
          "nodes":[{"name":"map","type":"mapping","mapping":{"orderId":"$0.id"}},
            {"name":"validate","type":"validate","schema":{}},
            {"name":"script","type":"script","language":"javascript","script":""}],
          "connections":[
            {"sourceNode":"map","sourceOutputPort":0,"targetNode":"validate","targetInputPort":0},
            {"sourceNode":"validate","sourceOutputPort":0,"targetNode":"script","targetInputPort":0}]}
        """);

    assertEquals("integer", binding(result, "$0").property("orderId").type());
    assertEquals("integer", binding(result, "orderId").type());
    assertFalse(binding(result, "$0").properties().containsKey("id"));
    assertTrue(result.diagnostics().isEmpty());
  }

  private CodeContextDescription scriptContext(String source) {
    var request = new CodeContextRequest("flow-editor", 1, 1, "logicflow.script", "javascript",
        new CodeContextRequest.Resource("logicflow", "flow"), new CodeContextRequest.Location("script", "/script"),
        Json.toBean(JsonNode.class, source));
    var view = new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.unavailable(), new ScriptIntelligenceSnapshot());
    return new LogicFlowCodeContextProvider().describe(request, view);
  }

  private TypeShape binding(CodeContextDescription result, String name) {
    return result.bindings().stream().filter(item -> name.equals(item.getName())).findFirst().orElseThrow().getShape();
  }
}
