package com.ouroboros.webapi;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebAPIModelContractTest {

  @Test
  void handlerTypeEnumHasFourExplicitValues() {
    HandlerType[] values = HandlerType.values();
    assertEquals(4, values.length);
  }

  @Test
  void handlerTypeEnumContainsExpectedConstants() {
    assertNotNull(HandlerType.valueOf("REQUEST_MAPPING"));
    assertNotNull(HandlerType.valueOf("ROUTER_FUNCTION"));
    assertNotNull(HandlerType.valueOf("LOGIC_FLOW"));
  }

  @Test
  void handlerTypeGetValueReturnsHumanReadableString() {
    assertEquals("RequestMapping",  HandlerType.REQUEST_MAPPING.getValue());
    assertEquals("RouterFunction",  HandlerType.ROUTER_FUNCTION.getValue());
    assertEquals("LogicFlow",       HandlerType.LOGIC_FLOW.getValue());
    assertEquals("BusinessAction", HandlerType.BUSINESS_ACTION.getValue());
  }

  @Test
  void handlerTypeFromValueReturnsCorrectConstant() {
    assertEquals(HandlerType.REQUEST_MAPPING, HandlerType.fromValue("RequestMapping"));
    assertEquals(HandlerType.ROUTER_FUNCTION, HandlerType.fromValue("RouterFunction"));
    assertEquals(HandlerType.LOGIC_FLOW,      HandlerType.fromValue("LogicFlow"));
    assertEquals(HandlerType.BUSINESS_ACTION, HandlerType.fromValue("BusinessAction"));
  }

  @Test
  void handlerTypeFromValueReturnsNullForUnknownString() {
    assertNull(HandlerType.fromValue("unknown"));
  }

  @Test
  void webAPIScopeEnumHasExactlyThreeValues() {
    WebAPIScope[] values = WebAPIScope.values();
    assertEquals(3, values.length);
  }

  @Test
  void webAPIScopeEnumContainsExpectedConstants() {
    assertNotNull(WebAPIScope.valueOf("PUBLIC"));
    assertNotNull(WebAPIScope.valueOf("OPEN"));
    assertNotNull(WebAPIScope.valueOf("INTERNAL"));
  }

  @Test
  void webAPIScopeGetValueReturnsLowercaseString() {
    assertEquals("public",   WebAPIScope.PUBLIC.getValue());
    assertEquals("open",     WebAPIScope.OPEN.getValue());
    assertEquals("internal", WebAPIScope.INTERNAL.getValue());
  }

  @Test
  void webAPIScopeGetPrefixReturnsPathPrefix() {
    assertEquals("/public-api", WebAPIScope.PUBLIC.getPrefix());
    assertEquals("/open-api",   WebAPIScope.OPEN.getPrefix());
    assertEquals("/api",        WebAPIScope.INTERNAL.getPrefix());
  }

  @Test
  void webAPIScopeFromValueReturnsCorrectConstant() {
    assertEquals(WebAPIScope.PUBLIC,   WebAPIScope.fromValue("public"));
    assertEquals(WebAPIScope.OPEN,     WebAPIScope.fromValue("open"));
    assertEquals(WebAPIScope.INTERNAL, WebAPIScope.fromValue("internal"));
  }

  @Test
  void webAPIScopeFromValueReturnsNullForUnknownString() {
    assertNull(WebAPIScope.fromValue("unknown"));
  }

  @Test
  void webAPIScopeFromPrefixReturnsCorrectConstant() {
    assertEquals(WebAPIScope.PUBLIC,   WebAPIScope.fromPrefix("/public-api"));
    assertEquals(WebAPIScope.OPEN,     WebAPIScope.fromPrefix("/open-api"));
    assertEquals(WebAPIScope.INTERNAL, WebAPIScope.fromPrefix("/api"));
  }

  @Test
  void webAPIScopeFromPrefixReturnsNullForUnknownPrefix() {
    assertNull(WebAPIScope.fromPrefix("/unknown"));
  }

  @Test
  void webAPIModelProviderImplementationReturnsNonNullMap() {
    WebAPIModelProvider provider = () -> Collections.singletonMap(
        "GET /users",
        SimpleWebAPIModel.builder().get("/users").build()
    );

    Map<String, WebAPIModel> map = provider.getWebAPIModelMap();
    assertNotNull(map);
    assertFalse(map.isEmpty());
  }

  @Test
  void webAPIModelProviderImplementationCanReturnEmptyMap() {
    WebAPIModelProvider emptyProvider = Collections::emptyMap;

    Map<String, WebAPIModel> map = emptyProvider.getWebAPIModelMap();
    assertNotNull(map);
    assertTrue(map.isEmpty());
  }

  @Test
  void webAPIRouteRegistryAcceptsCollectionOfModels() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder().get("/items").build();
    java.util.List<Boolean> called = new java.util.ArrayList<>();

    WebAPIRouteRegistry registry = models -> {
      assertNotNull(models);
      called.add(Boolean.TRUE);
    };

    registry.register(Collections.singletonList(model));
    assertEquals(1, called.size());
  }

  @Test
  void webAPIBasePathSupplierReturnsNonNullBasePath() {
    WebAPIBasePathSupplier supplier = () -> "/api/v1";

    String basePath = supplier.getWebAPIBasePath();
    assertNotNull(basePath);
    assertEquals("/api/v1", basePath);
  }

  @Test
  void webAPIModelDefaultMethodsReturnSafeEmptyValues() {
    WebAPIModel model = SimpleWebAPIModel.builder().get("/x").build();

    assertNotNull(model.getTags());
    assertTrue(model.getTags().isEmpty());
    assertNotNull(model.getParameters());
    assertTrue(model.getParameters().isEmpty());
    assertNotNull(model.getResponses());
    assertTrue(model.getResponses().isEmpty());
    assertNotNull(model.getSecurity());
    assertTrue(model.getSecurity().isEmpty());
    assertNotNull(model.getServers());
    assertTrue(model.getServers().isEmpty());
    assertNull(model.getRequestBody());
    assertNull(model.getExternalDocs());
    assertNull(model.getOperationId());
    assertNull(model.getDescription());
  }

  @Test
  void webAPIModelImplementsWebAPIModelInterface() {
    SimpleWebAPIModel model = SimpleWebAPIModel.builder().get("/x").build();
    assertTrue(model instanceof WebAPIModel);
  }

  @Test
  void nullOptionalActionPropertiesAreEquivalentToAbsentProperties() {
    assertDoesNotThrow(() -> BusinessActionWebApiContract.validate(SimpleWebAPIModel.builder().get("/ordinary")
        .handlerType(HandlerType.REQUEST_MAPPING).extraProp("businessModel", null)
        .extraProp("action", null).extraProp("actionInputMapping", null).build()));
    assertDoesNotThrow(() -> BusinessActionWebApiContract.validate(SimpleWebAPIModel.builder().post("/approve")
        .handlerType(HandlerType.BUSINESS_ACTION).extraProp("businessModel", "sales.Order")
        .extraProp("action", "approve").extraProp("actionInputMapping", Map.of())
        .extraProp("formatVersion", "2.0").extraProp("logicFlow", null)
        .extraProp("businessActionName", null).build()));
  }

  @Test
  void actionMappingUsesTheSourceRouteVersionAndRejectsUnknownRawVersions() {
    var current = SimpleWebAPIModel.builder().get("/orders/{id}").handlerType(HandlerType.BUSINESS_ACTION)
        .extraProp("businessModel", "legacy.Order").extraProp("action", "read")
        .extraProp("formatVersion", "2.0")
        .extraProp("actionInputMapping", Map.of("params.id", "${ request.pathVariables.id }")).build();
    assertDoesNotThrow(() -> BusinessActionWebApiContract.validate(current));
    assertEquals("one", BusinessActionWebApiContract.compileInput(current).get("params.id")
        .evaluate(Map.of("request", Map.of("pathVariables", Map.of("id", "one")))).get());
    for (Object invalid : java.util.List.of("", "1.0", "3.0", 2, Map.of())) {
      var model = current.map(builder -> builder.extraProp("formatVersion", invalid));
      assertThrows(IllegalArgumentException.class, () -> BusinessActionWebApiContract.validate(model));
    }
    assertThrows(IllegalArgumentException.class, () -> BusinessActionWebApiContract.validate(
        current.map(builder -> builder.extraProp("formatVersion", null))));
    assertThrows(IllegalArgumentException.class, () -> BusinessActionWebApiContract.validate(
        current.map(builder -> builder.extraProp("actionInputMapping", Map.of("params.id", "$.pathVariables.id")))));
    assertDoesNotThrow(() -> BusinessActionWebApiContract.validate(SimpleWebAPIModel.builder().get("/ordinary")
        .handlerType(HandlerType.REQUEST_MAPPING).extraProp("formatVersion", 99).build()));
  }

  @Test
  void actionMappingChecksOfficialExpressionSyntaxAndDeclaredPathVariablesBeforeExecution() {
    assertThrows(IllegalArgumentException.class, () -> BusinessActionWebApiContract.validateMapping(
        Map.of("params.id", "${ request.pathVariables.missing }"), "/orders/{id}", "2.0"));
    assertThrows(IllegalArgumentException.class, () -> BusinessActionWebApiContract.validateMapping(
        Map.of("data", "${ request.body[ }"), "/orders", "2.0"));
    assertDoesNotThrow(() -> BusinessActionWebApiContract.validateMapping(
        Map.of("data", "${ 1 / 0 }"), "/orders", "2.0"));
  }
}
