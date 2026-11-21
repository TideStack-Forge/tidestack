package com.ouroboros.businessmodel.dev.service.impl;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessModelDevServiceFormatTest {
  @Test
  void fileAndRequestReadersRejectInvalidVersionsBeforeAnyFallback() {
    var service = new BusinessModelDevService();
    for (String raw : List.of("null", "2", "true", "\"\"", "[]", "{}", "\"unknown\"")) {
      String content = "{\"modelType\":\"default\",\"name\":\"Order\",\"formatVersion\":" + raw + "}";
      assertTrue(service.buildModelFromFileContent(content).isFailure(), raw);
      assertTrue(service.buildModelFromRequestBody(content).isFailure(), raw);
    }
  }

  @Test
  void ordinaryEditingPreservesMissingVersionAndNestedLegacyDefinitions() {
    var service = new SerializationService();
    var legacyApi = Map.of("isEnabled", true, "isBindAction", false,
        "actionInputMapping", Map.of("data", "$.body"), "unknownOption", List.of("keep"));
    String content = Json.toJsonString(Map.of("modelType", "default", "name", "Order",
        "custom", List.of(Map.of("name", "approve", "api", legacyApi)),
        "unknownRoot", Map.of("future", true), "validationState", "VALID"));
    var meta = service.buildModelFromRequestBody(content).get();
    meta.setTitle("Edited title");
    var saved = Json.toMap(service.serialize(meta));
    assertFalse(saved.containsKey("formatVersion"));
    assertFalse(saved.containsKey("actions"));
    assertFalse(saved.containsKey("events"));
    assertFalse(saved.containsKey("eventSubscriptions"));
    assertFalse(saved.containsKey("validationState"));
    assertEquals("Edited title", saved.get("title"));
    assertEquals(Json.toMap(content).get("custom"), saved.get("custom"));
    assertEquals(Map.of("future", true), saved.get("unknownRoot"));
    assertTrue(service.buildModelFromFileContent(service.serialize(meta)).isSuccess());
  }

  @Test
  void ordinaryEditingRetainsExplicitEmptyCollectionsAndUnknownBehaviorOptions() {
    var service = new SerializationService();
    var extension = Map.of("future", List.of("preserve", 1));
    for (String version : List.of("legacy", "2.0")) {
      var document = new java.util.LinkedHashMap<String, Object>(Map.of(
          "modelType", "default", "name", "Order", "actions", List.of(Map.of(
              "name", "approve", "futureAction", extension,
              "binding", Map.of("kind", "handler", "handlerName", "approveHandler", "futureBinding", extension),
              "emits", List.of(Map.of("eventName", "approved", "futureReference", extension)))),
          "events", List.of(Map.of("name", "approved", "futureEvent", extension)),
          "eventSubscriptions", List.of(Map.of("name", "notify", "futureSubscription", extension))));
      if (!"legacy".equals(version)) { document.put("formatVersion", version); }
      var meta = service.buildModelFromRequestBody(Json.toJsonString(document)).get();
      meta.setTitle("Only title edited");
      var saved = Json.toMap(service.serialize(meta));
      var action = (Map<?, ?>) ((List<?>) saved.get("actions")).getFirst();
      assertEquals(extension, action.get("futureAction"));
      assertEquals(extension, ((Map<?, ?>) action.get("binding")).get("futureBinding"));
      assertEquals(extension, ((Map<?, ?>) ((List<?>) action.get("emits")).getFirst()).get("futureReference"));
      assertEquals(extension, ((Map<?, ?>) ((List<?>) saved.get("events")).getFirst()).get("futureEvent"));
      assertEquals(extension, ((Map<?, ?>) ((List<?>) saved.get("eventSubscriptions")).getFirst()).get("futureSubscription"));
      var empty = service.buildModelFromRequestBody("{\"modelType\":\"default\",\"name\":\"Order\",\"actions\":[],\"events\":[],\"eventSubscriptions\":[]}").get();
      var emptySaved = Json.toMap(service.serialize(empty));
      for (String key : List.of("actions", "events", "eventSubscriptions")) {
        assertEquals(List.of(), emptySaved.get(key));
      }
    }
  }

  @Test
  void ordinaryEditingDoesNotMaterializeMissingBehaviorDefaults() {
    var service = new SerializationService();
    var action = Map.of("name", "approve", "binding", Map.of("kind", "handler", "handlerName", "approveHandler"));
    var event = Map.of("name", "approved");
    var subscription = Map.of("name", "notify");
    for (String version : List.of("legacy", "2.0")) {
      var document = new java.util.LinkedHashMap<String, Object>(Map.of(
          "modelType", "default", "name", "Order", "actions", List.of(action),
          "events", List.of(event), "eventSubscriptions", List.of(subscription)));
      if (!"legacy".equals(version)) { document.put("formatVersion", version); }
      var meta = service.buildModelFromRequestBody(Json.toJsonString(document)).get();
      var defaultMeta = DefaultBusinessModelMeta.class.cast(meta);
      var parsedAction = defaultMeta.getActions().getFirst();
      assertFalse(parsedAction.getIsEnabled());
      assertEquals(Map.of(), parsedAction.getInputSchema());
      assertEquals(Map.of(), parsedAction.getOutputSchema());
      assertEquals(List.of(), parsedAction.getEmits());
      assertEquals(Map.of(), parsedAction.getBinding().getInputMapping());
      assertFalse(parsedAction.getBinding().isRequiresHttpRequest());
      var parsedEvent = defaultMeta.getEvents().getFirst();
      assertEquals(Map.of(), parsedEvent.getPayloadSchema());
      assertEquals(List.of(), parsedEvent.getAllowedProducerKinds());
      assertFalse(parsedEvent.isRequireUnfilteredModelRead());
      assertNull(defaultMeta.getEventSubscriptions().getFirst().getTarget().getInputMapping());
      meta.setTitle("Only title edited");
      var saved = Json.toMap(service.serialize(meta));
      var savedAction = (Map<?, ?>) ((List<?>) saved.get("actions")).getFirst();
      var savedBinding = (Map<?, ?>) savedAction.get("binding");
      assertFalse(savedAction.containsKey("isEnabled"));
      assertFalse(savedAction.containsKey("inputSchema"));
      assertFalse(savedAction.containsKey("outputSchema"));
      assertFalse(savedAction.containsKey("emits"));
      assertFalse(savedBinding.containsKey("inputMapping"));
      assertFalse(savedBinding.containsKey("requiresHttpRequest"));
      var savedEvent = (Map<?, ?>) ((List<?>) saved.get("events")).getFirst();
      assertFalse(savedEvent.containsKey("requireUnfilteredModelRead"));
      assertFalse(savedEvent.containsKey("payloadSchema"));
      assertFalse(savedEvent.containsKey("allowedProducerKinds"));
      assertFalse(((Map<?, ?>) ((List<?>) saved.get("eventSubscriptions")).getFirst()).containsKey("inputMapping"));
    }
  }

  @Test
  void ordinaryEditingRetainsExplicitBehaviorDefaults() {
    var service = new SerializationService();
    var action = Map.of("name", "approve", "isEnabled", false, "inputSchema", Map.of(),
        "outputSchema", Map.of(), "emits", List.of(), "binding", Map.of(
            "kind", "handler", "handlerName", "approveHandler", "inputMapping", Map.of(), "requiresHttpRequest", false));
    var event = Map.of("name", "approved", "payloadSchema", Map.of(),
        "allowedProducerKinds", List.of(), "requireUnfilteredModelRead", false);
    var subscription = Map.of("name", "notify", "target", Map.of("kind", "listener", "inputMapping", Map.of()));
    var document = Map.of("modelType", "default", "name", "Order", "actions", List.of(action),
        "events", List.of(event), "eventSubscriptions", List.of(subscription));
    var meta = service.buildModelFromRequestBody(Json.toJsonString(document)).get();
    meta.setTitle("Only title edited");
    var saved = Json.toMap(service.serialize(meta));
    var savedAction = (Map<?, ?>) ((List<?>) saved.get("actions")).getFirst();
    assertEquals(false, savedAction.get("isEnabled"));
    assertEquals(Map.of(), savedAction.get("inputSchema"));
    assertEquals(Map.of(), savedAction.get("outputSchema"));
    assertEquals(List.of(), savedAction.get("emits"));
    var savedBinding = (Map<?, ?>) savedAction.get("binding");
    assertEquals(Map.of(), savedBinding.get("inputMapping"));
    assertEquals(false, savedBinding.get("requiresHttpRequest"));
    var savedEvent = (Map<?, ?>) ((List<?>) saved.get("events")).getFirst();
    assertEquals(false, savedEvent.get("requireUnfilteredModelRead"));
    assertEquals(Map.of(), savedEvent.get("payloadSchema"));
    assertEquals(List.of(), savedEvent.get("allowedProducerKinds"));
    assertEquals(Map.of(), ((Map<?, ?>) ((Map<?, ?>) ((List<?>) saved.get("eventSubscriptions")).getFirst()).get("target")).get("inputMapping"));
  }

  @Test
  void semanticDescriptionsSurviveOrdinarySaveWithoutFillingMissingValuesOrChangingVersions() {
    var service = new SerializationService();
    String description = "业务含义\n单位、使用口径与限制；${ 1 / 0 } 不求值";
    for (String version : List.of("legacy", "0.1", "1.0", "2.0")) {
      var document = new java.util.LinkedHashMap<String, Object>(Map.of(
          "modelType", "default", "name", "Order", "fields", List.of(
              Map.of("name", "amount", "title", "金额", "type", "整数数字", "description", description),
              Map.of("name", "state", "title", "状态", "type", "单行文本"),
              Map.of("name", "note", "title", "备注", "type", "单行文本", "description", "")),
          "relations", List.of(Map.of("name", "billTo", "title", "开票客户", "model", "sales.Customer", "type", "reference",
              "description", description, "futureOption", "preserve"),
              Map.of("name", "shipTo", "title", "收货客户", "model", "sales.Customer", "type", "reference"))));
      if (!"legacy".equals(version)) { document.put("formatVersion", version); }
      var parsed = service.buildModelFromRequestBody(Json.toJsonString(document)).get();
      parsed.setTitle("编辑后的标题");
      var saved = Json.toMap(service.serialize(parsed));
      var originalFields = (List<?>) document.get("fields");
      var fields = (List<?>) saved.get("fields");
      assertEquals(description, ((Map<?, ?>) fields.getFirst()).get("description"));
      assertFalse(((Map<?, ?>) fields.get(1)).containsKey("description"));
      assertEquals("", ((Map<?, ?>) fields.get(2)).get("description"));
      assertEquals(((Map<?, ?>) originalFields.get(1)).get("title"), ((Map<?, ?>) fields.get(1)).get("title"));
      var relations = (List<?>) saved.get("relations");
      assertEquals(description, ((Map<?, ?>) relations.getFirst()).get("description"));
      assertEquals("preserve", ((Map<?, ?>) relations.getFirst()).get("futureOption"));
      assertFalse(((Map<?, ?>) relations.get(1)).containsKey("description"));
      assertEquals(document.get("formatVersion"), saved.get("formatVersion"));
      var reloaded = service.buildModelFromFileContent(service.serialize(parsed)).get();
      assertEquals(saved, Json.toMap(service.serialize(reloaded)));
    }
  }

  private static final class SerializationService extends BusinessModelDevService {
    String serialize(BusinessModelMeta model) {
      return getPermanentJson(model).get();
    }
  }
}
