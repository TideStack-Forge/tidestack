package com.ouroboros.businessmodel.dev;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.businessmodel.dev.service.impl.BusinessModelDevService;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.dev.metadata.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.util.Json;

class BusinessModelCodeContextProviderTest {
  private final BusinessModelCodeContextProvider provider = new BusinessModelCodeContextProvider(new BusinessModelDevService() {
    @Override public List<com.ouroboros.businessmodel.meta.BusinessModelMeta> getAll() { return List.of(); }
  });
  private final ReadOnlyMetadataView empty = new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.unavailable(), new ScriptIntelligenceSnapshot());
  private CodeContextRequest request(String site, String language, String pointer, String draft) {
    return new CodeContextRequest("business-editor", 1, 1, site, language, new CodeContextRequest.Resource("business-model", "Order"),
        new CodeContextRequest.Location(null, pointer), Json.toBean(JsonNode.class, draft));
  }
  @Test void restrictedPreconditionsAndMappingsUseTheirExecutionRoots() {
    var draft = """
        {"name":"Order","formatVersion":"2.0","actions":[{"name":"approve","inputSchema":{"type":"object","properties":{"id":{"type":"integer"}}},
        "outputSchema":{"type":"object","properties":{"approved":{"type":"boolean"}}},"binding":{"flowName":"demo.approve"}}]}
        """;
    var condition = provider.describe(request("business-model.precondition", "spel", "/actions/0/precondition/expression", draft), empty);
    assertFalse(condition.allowGlobals()); assertEquals("boolean", condition.expectedResult().type());
    assertEquals(List.of("input", "objectStates"), condition.bindings().stream().map(binding -> binding.getName()).toList());
    var mapping = provider.describe(request("business-model.mapping", "spel", "/actions/0/binding/inputMapping", draft), empty);
    assertTrue(mapping.allowGlobals()); assertEquals("integer", mapping.bindings().getFirst().getShape().property("id").type());
    assertEquals("map", mapping.bindings().get(1).getShape().representation());
    var output = provider.describe(request("business-model.mapping", "spel", "/actions/0/binding/outputMapping", draft), empty);
    assertTrue(output.bindings().getFirst().getShape().isUnknown(), "Action output must not masquerade as raw flow output");
    assertThrows(BusinessException.class, () -> provider.describe(request("invalid", "spel", "/actions/9/precondition/expression", draft), empty));
    assertThrows(BusinessException.class, () -> provider.describe(request("invalid", "javascript", "/actions/0/precondition/expression", draft), empty));
  }
  @Test void externalEventPayloadAndLegacyDialectRemainOwnedByTheirSource() {
    var model = new Metadata(); model.setName("other.Source"); model.setDetails(Map.of("events", List.of(Map.of("name", "changed",
        "payloadSchema", Map.of("type", "object", "properties", Map.of("external", Map.of("type", "boolean")))))));
    var view = new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.current(Map.of("business-model", List.of(model))), new ScriptIntelligenceSnapshot());
    var draft = """
        {"name":"Order","events":[{"name":"changed","payloadSchema":{"type":"object","properties":{"wrong":{"type":"string"}}}}],
        "eventSubscriptions":[{"sourceEvent": {"modelFullName": "other.Source", "eventName": "changed"}, "target": {"kind": "action"}, "executionMode": "AFTER_COMMIT_TRANSACTIONAL"}]}
        """;
    var filter = provider.describe(request("business-model.event-filter", "spel", "/eventSubscriptions/0/filter", draft), view);
    assertFalse(filter.allowGlobals()); assertEquals("boolean", filter.bindings().getFirst().getShape().property("payload").property("external").type());
    assertTrue(filter.bindings().getFirst().getShape().property("payload").property("wrong").isUnknown());
    var input = provider.describe(request("business-model.mapping", "spel", "/eventSubscriptions/0/target/inputMapping", draft), view);
    assertEquals("template", input.projectionHints().get("embeddedExpressionMode"));
    var listener = provider.describe(request("business-model.mapping", "spel", "/eventSubscriptions/0/target/inputMapping", draft), view);
    assertEquals("template", listener.projectionHints().get("embeddedExpressionMode"));
  }
  @Test void fieldDefaultsDescribeRecordNamesWithoutInventingDraftFieldTypes() {
    var description = provider.describe(request("business-model.field-default", "spel", "/fields/0/defaultValue",
        "{\"name\":\"Order\",\"fields\":[{\"name\":\"title\",\"type\":\"text\"}]}"), empty);
    assertEquals(List.of("title", "$record"), description.bindings().stream().map(binding -> binding.getName()).toList());
    assertTrue(description.bindings().getLast().getShape().properties().containsKey("title"));
  }
  @Test void generatedCrudScriptsResolveSourcePointersThroughTheFormalProjection() {
    var draft = """
        {"modelType":"default","name":"Order","title":"订单","isEnabled":true,"fields":[{"name":"id","type":"单行文本","isPrimaryKey":true}],"apis":{
          "add":{"isEnabled":true,"beforeScript":"return $0","beforeScriptLanguage":"javascript"}}}
        """;
    var description = provider.describe(request("business-model.api-script", "javascript", "/apis/add/beforeScript", draft), empty);
    assertTrue(description.bindings().stream().anyMatch(binding -> "$0".equals(binding.getName())));
    assertTrue(description.bindings().stream().anyMatch(binding -> "$request".equals(binding.getName())), "Use projected port names");
  }
  @Test void everyCrudScriptSlotResolvesItsFormalProjection() {
    for (var operation : List.of("add", "update", "delete", "getOne", "search")) {
      for (var slot : List.of("beforeScript", "afterScript", "script")) {
        var config = new java.util.LinkedHashMap<String, Object>();
        config.put("isEnabled", true); config.put("isAuto", !slot.equals("script"));
        config.put(slot, "return $0"); config.put(slot + "Language", "javascript");
        var draft = Map.of("modelType", "default", "name", "Order", "title", "订单", "isEnabled", true,
            "fields", List.of(Map.of("name", "id", "type", "单行文本", "isPrimaryKey", true)), "apis", Map.of(operation, config));
        var result = provider.describe(request("business-model.api-script", "javascript", "/apis/" + operation + "/" + slot,
            Json.toJsonString(draft)), empty);
        assertTrue(result.bindings().stream().anyMatch(binding -> "$0".equals(binding.getName())), operation + ":" + slot);
      }
    }
  }

}
