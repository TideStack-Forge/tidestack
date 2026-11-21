package com.ouroboros.webapi;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

import com.ouroboros.businessmodel.action.PreparedBusinessActionInput;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.adapter.RequestInfoMap;
import com.ouroboros.util.Json;
import com.ouroboros.webapi.openapi.ParameterLocation;
import com.ouroboros.webapi.openapi.ParameterModel;
import com.ouroboros.webapi.openapi.SchemaModel;

import static org.junit.jupiter.api.Assertions.*;

class BusinessActionHttpInputPlanTest {
  @Test
  void explicitUpgradeReusesHttpSourcesAndPreservesPresenceHeaderAndArraySemantics() {
    var old = Map.of("params.ids", "$.query.ids", "params.token", "$.headers.x-token", "data", "$.body");
    var upgraded = BusinessActionWebApiContract.upgradeMapping(old, "/orders");
    var parameter = ParameterModel.builder().name("ids").in(ParameterLocation.QUERY)
        .schema(SchemaModel.builder().type("array").build()).build();
    var model = SimpleWebAPIModel.builder().api("POST", "/orders").handlerType(HandlerType.BUSINESS_ACTION)
        .parameters(List.of(parameter)).extraProp("actionInputMapping", upgraded).extraProp("formatVersion", "2.0").build();
    for (String body : List.of("", "null", "{\"note\":\"ok\"}")) {
      var servlet = servlet(body, "ids=one&ids=two"); servlet.addHeader("X-Token", "same");
      var request = serverRequest(servlet);
      var info = RequestInfoMap.forRequest(request);
      var before = new BusinessActionHttpInputPlan(old, List.of(parameter)).read(request, info);
      var after = new BusinessActionHttpInputPlan(model).read(request, info);
      assertEquals(before, after);
      assertEquals(before.containsKey("data"), after.containsKey("data"));
      if (after.containsKey("data")) { assertSame(info.get("body"), after.get("data")); }
    }
    assertThrows(IllegalArgumentException.class, () -> BusinessActionWebApiContract.upgradeMapping(
        Map.of("params.id", "$.pathVariables.id"), "/orders"));
  }

  @Test
  void repeatedQueryArraysKeepSingletonsOrderAndDecodedValuesWithoutReadingFormOrHeaders() {
    var parameter = ParameterModel.builder().name("ids").in(ParameterLocation.QUERY)
        .schema(SchemaModel.builder().type("array").build()).build();
    var plan = new BusinessActionHttpInputPlan(Map.of("params.records[].id", "$.query.ids",
        "data.name", "$.query.name"), List.of(parameter));
    for (String query : List.of("ids=one&name=a%2Bb", "ids=one&ids=two&name=a%2Bb")) {
      var request = request("", query);
      var input = plan.read(request, RequestInfoMap.forRequest(request));
      var params = assertInstanceOf(Map.class, input.get("params"));
      assertEquals(query.contains("ids=two") ? List.of(Map.of("id", "one"), Map.of("id", "two"))
          : List.of(Map.of("id", "one")), params.get("records"));
      assertEquals(Map.of("name", "a+b"), input.get("data"));
    }
  }

  @Test
  void headersAreCaseInsensitiveAndBusinessScalarsRejectMultipleValues() {
    var plan = new BusinessActionHttpInputPlan(Map.of("params.token", "$.headers.x-token"), List.of());
    var servlet = servlet("", "");
    servlet.addHeader("X-Token", "first");
    var request = serverRequest(servlet);
    assertEquals(Map.of("params", Map.of("token", "first")), plan.read(request, RequestInfoMap.forRequest(request)));
    servlet.addHeader("X-Token", "second");
    var multiple = new BusinessActionWebApiAdapter.HttpBusinessActionInput(serverRequest(servlet), plan);
    assertThrows(BusinessException.class, () -> PreparedBusinessActionInput.prepare(multiple,
        Map.of("type", "object", "properties", Map.of("params", Map.of("type", "object",
            "properties", Map.of("token", Map.of("type", "string")))))));
  }

  @Test
  void distinguishesAbsentBodyAndExplicitNullAndProjectsOrderedBodyArrays() {
    var whole = new BusinessActionHttpInputPlan(Map.of("data", "$.body"), List.of());
    var absent = request("", "");
    assertEquals(Map.of(), whole.read(absent, RequestInfoMap.forRequest(absent)));
    var explicitNull = request("null", "");
    var values = whole.read(explicitNull, RequestInfoMap.forRequest(explicitNull));
    assertTrue(values.containsKey("data"));
    assertNull(values.get("data"));
    var request = request("{\"ids\":[\"one\",\"two\"]}", "");
    var items = new BusinessActionHttpInputPlan(Map.of("params.records[].id", "$.body.ids"), List.of());
    assertEquals(Map.of("params", Map.of("records", List.of(Map.of("id", "one"), Map.of("id", "two")))),
        items.read(request, RequestInfoMap.forRequest(request)));
    var scalar = request("{\"ids\":\"one\"}", "");
    assertThrows(BusinessException.class, () -> ((Map<?, ?>) items.read(scalar, RequestInfoMap.forRequest(scalar)).get("params")).get("records"));
  }

  @Test
  void rejectsUnsupportedQueryArraySerializationWhileCompilingTheRoute() {
    var parameter = ParameterModel.builder().name("ids").in(ParameterLocation.QUERY)
        .schema(SchemaModel.builder().type("array").build()).extraProp("explode", false).build();
    assertThrows(IllegalArgumentException.class, () -> new BusinessActionHttpInputPlan(
        Map.of("params.ids", "$.query.ids"), List.of(parameter)));
  }

  @Test
  void inputIsAKeyBasedViewAndParameterReadsDoNotTouchTheBody() {
    var request = request("{\"comment\":\"ok\"}", "notify=true");
    var bodyReads = new java.util.concurrent.atomic.AtomicInteger();
    var info = new RequestInfoMap(request) {
      @Override public Object get(Object key) {
        if ("body".equals(key)) { bodyReads.incrementAndGet(); }
        return super.get(key);
      }
    };
    request.servletRequest().setAttribute(RequestInfoMap.class.getName(), info);
    var plan = new BusinessActionHttpInputPlan(Map.of("params.notify", "$.query.notify", "data", "$.body"), List.of());
    var input = new BusinessActionWebApiAdapter.HttpBusinessActionInput(request, plan);
    assertEquals(0, bodyReads.get());
    assertTrue(input.hasParams());
    assertTrue(input.hasData());
    assertEquals(java.util.Set.of("params", "data"), input.keySet());
    assertEquals(java.util.Set.of("params", "data"), input.entrySet().stream()
        .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
    assertEquals(0, bodyReads.get());
    assertEquals("true", assertInstanceOf(Map.class, input.params()).get("notify"));
    assertEquals(0, bodyReads.get());
    var dataEntry = input.entrySet().stream().filter(entry -> "data".equals(entry.getKey())).findFirst().orElseThrow();
    assertEquals(0, bodyReads.get());
    assertEquals(Map.of("comment", "ok"), dataEntry.getValue());
    assertSame(dataEntry.getValue(), input.data());
    assertEquals(1, bodyReads.get());
    var prepared = PreparedBusinessActionInput.prepare(input, Map.of("type", "object", "properties",
        Map.of("params", Map.of("type", "object", "properties", Map.of("notify", Map.of("type", "boolean"))),
            "data", Map.of("type", "object"))));
    assertSame(input, prepared);
    assertEquals(true, assertInstanceOf(Map.class, prepared.params()).get("notify"));
    assertEquals(1, bodyReads.get());
  }

  @Test
  void unusedTransportBodyIsNotParsedEvenWhenTheActionInputIsValidated() {
    var request = request("invalid JSON", "notify=true");
    var plan = new BusinessActionHttpInputPlan(Map.of("params.notify", "$.query.notify"), List.of());
    var input = new BusinessActionWebApiAdapter.HttpBusinessActionInput(request, plan);
    var prepared = PreparedBusinessActionInput.prepare(input, Map.of("type", "object", "properties",
        Map.of("params", Map.of("type", "object", "properties", Map.of("notify", Map.of("type", "boolean"))))));
    assertEquals(Map.of("notify", true), prepared.params());
    assertFalse(prepared.hasData());
    assertThrows(BusinessException.class, () -> input.currentRequest().get("body"));
  }

  @Test
  void bodyPropertyPresenceRequiresParsingButKeepsMissingAndNullDistinct() {
    var request = request("{\"comment\":null}", "");
    var plan = new BusinessActionHttpInputPlan(Map.of("data.comment", "$.body.comment",
        "data.absent", "$.body.absent"), List.of());
    var input = plan.read(request, RequestInfoMap.forRequest(request));
    var data = assertInstanceOf(Map.class, input.get("data"));
    assertTrue(data.containsKey("comment"));
    assertNull(data.get("comment"));
    assertFalse(data.containsKey("absent"));
    assertEquals(java.util.Set.of("comment"), data.keySet());
  }

  @Test
  void failedBodyReadIsCachedWithoutPreventingIndependentParameterReads() {
    var request = request("invalid JSON", "id=one");
    var plan = new BusinessActionHttpInputPlan(Map.of("params.id", "$.query.id", "data", "$.body"), List.of());
    var input = new BusinessActionWebApiAdapter.HttpBusinessActionInput(request, plan);
    var failure = assertThrows(BusinessException.class, input::data);
    assertSame(failure, assertThrows(BusinessException.class, input::data));
    assertEquals(Map.of("id", "one"), input.params());
    assertThrows(BusinessException.class, () -> PreparedBusinessActionInput.prepare(input, Map.of()));
  }

  @Test
  void destinationConflictsAreRejectedBeforeLazyProjectionCanHideAValue() {
    for (var mapping : List.of(
        Map.of("params.records", "$.body.records", "params.records[].id", "$.query.ids"),
        Map.of("params.records.id", "$.query.id", "params.records[].id", "$.query.ids"),
        Map.of("params.records.name", "$.query.name", "params.records[].id", "$.query.ids"),
        Map.of("params.records[].name", "$.query.names", "params.records[].id", "$.query.ids"))) {
      assertThrows(IllegalArgumentException.class, () -> BusinessActionWebApiContract.validateMapping(mapping, "/orders"));
    }
    var mapping = Map.of("params.record.id", "$.query.id", "params.record.name", "$.query.name");
    BusinessActionWebApiContract.validateMapping(mapping, "/orders");
    var request = request("", "id=one&name=order");
    var input = new BusinessActionHttpInputPlan(mapping, List.of()).read(request, RequestInfoMap.forRequest(request));
    assertEquals(Map.of("params", Map.of("record", Map.of("id", "one", "name", "order"))), input);
  }

  @Test
  void selectedHeaderReadsDoNotEnumerateUnrelatedHeaderValues() {
    var unusedReads = new java.util.concurrent.atomic.AtomicInteger();
    var selectedReads = new java.util.concurrent.atomic.AtomicInteger();
    var servlet = new MockHttpServletRequest("GET", "/orders") {
      @Override public java.util.Enumeration<String> getHeaders(String name) {
        if ("X-Unused".equalsIgnoreCase(name)) { unusedReads.incrementAndGet(); }
        if ("X-Token".equalsIgnoreCase(name)) { selectedReads.incrementAndGet(); }
        return super.getHeaders(name);
      }
    };
    servlet.addHeader("X-Token", "selected");
    servlet.addHeader("X-Unused", "unrelated");
    var request = serverRequest(servlet);
    unusedReads.set(0);
    selectedReads.set(0);
    var input = new BusinessActionWebApiAdapter.HttpBusinessActionInput(request,
        new BusinessActionHttpInputPlan(Map.of("params.token", "$.headers.x-token"), List.of()));
    assertEquals(Map.of("token", "selected"), input.params());
    assertEquals(Map.of("token", "selected"), input.params());
    assertEquals(0, unusedReads.get());
    assertEquals(1, selectedReads.get());
  }

  @Test
  void officialExpressionsKeepTheOriginalRequestAndBodyAndReadComputedValuesLazily() {
    var request = request("{\"comment\":\"ok\"}", "");
    assertInstanceOf(MockHttpServletRequest.class, request.servletRequest()).setParameter("id", "query-id");
    var reads = new java.util.concurrent.atomic.AtomicInteger();
    var info = new RequestInfoMap(request) {
      @Override public Object get(Object key) {
        if ("body".equals(key)) { reads.incrementAndGet(); }
        return super.get(key);
      }
    };
    request.servletRequest().setAttribute(RequestInfoMap.class.getName(), info);
    var input = new BusinessActionWebApiAdapter.HttpBusinessActionInput(request, currentPlan(Map.of(
        "params.record.id", "${ request.params.id }", "data", "${ request.body ?: null }")));
    assertSame(info, input.currentRequest());
    assertTrue(input.hasData());
    assertEquals(java.util.Set.of("params", "data"), input.keySet());
    assertEquals(0, reads.get());
    assertEquals(Map.of("record", Map.of("id", "query-id")), input.params());
    assertEquals(0, reads.get());
    assertSame(input.data(), input.data());
    assertEquals(1, reads.get());
    assertSame(input.data(), info.get("body"));
  }

  @Test
  void officialSelectorsDistinguishMissingAndNullAndSchemaDefaultsOnlyFillMissing() {
    var request = request("{\"comment\":null}", "");
    var values = currentPlan(Map.of("data.comment", "${ request.body.comment }",
        "data.absent", "${ request.body.absent }", "params.limit", "${ request.params.limit ?: 20 }"))
        .read(request, RequestInfoMap.forRequest(request));
    var data = assertInstanceOf(Map.class, values.get("data"));
    assertTrue(data.containsKey("comment"));
    assertNull(data.get("comment"));
    assertFalse(data.containsKey("absent"));
    assertEquals(Map.of("limit", 20), values.get("params"));
    var absentRequest = request("", "");
    var input = new BusinessActionWebApiAdapter.HttpBusinessActionInput(absentRequest,
        currentPlan(Map.of("data", "${ request.body }")));
    assertFalse(input.hasData());
    var schema = Map.<String, Object>of("type", "object", "properties", Map.of("data",
        Map.of("type", List.of("string", "null"), "default", "fallback")));
    assertEquals("fallback", PreparedBusinessActionInput.prepare(input, schema).data());
    var nullRequest = request("null", "");
    var nullInput = new BusinessActionWebApiAdapter.HttpBusinessActionInput(nullRequest,
        currentPlan(Map.of("data", "${ request.body }")));
    assertTrue(nullInput.hasData());
    assertNull(PreparedBusinessActionInput.prepare(nullInput, schema).data());
  }

  @Test
  void officialHeaderSourcesKeepTransportArraysDistinctFromTheRawRequest() {
    var servlet = servlet("", "");
    servlet.addHeader("X-Token", "only");
    var request = serverRequest(servlet);
    var input = currentPlan(Map.of("params.token", "${ headers['x-token'] }",
        "data.tokens", "${ request.headers['X-Token'] }"))
        .read(request, RequestInfoMap.forRequest(request));
    assertEquals(Map.of("token", "only"), input.get("params"));
    assertEquals(Map.of("tokens", List.of("only")), input.get("data"));
    var parameter = ParameterModel.builder().name("X-Token").in(ParameterLocation.HEADER)
        .schema(SchemaModel.builder().type("array").build()).build();
    var model = SimpleWebAPIModel.builder().get("/orders").extraProp("formatVersion", "2.0")
        .parameters(List.of(parameter)).extraProp("actionInputMapping", Map.of("params.token", "${ headers['x-token'] }")).build();
    assertEquals(Map.of("params", Map.of("token", List.of("only"))),
        new BusinessActionHttpInputPlan(model).read(request, RequestInfoMap.forRequest(request)));
  }

  @Test
  void officialComputedFailuresAreCachedWithoutBlockingIndependentParameters() {
    var request = request("invalid JSON", "");
    assertInstanceOf(MockHttpServletRequest.class, request.servletRequest()).setParameter("id", "one");
    var input = new BusinessActionWebApiAdapter.HttpBusinessActionInput(request, currentPlan(Map.of(
        "params.id", "${ request.params.id }", "data", "${ 1 / 0 }")));
    assertTrue(input.hasData());
    assertEquals(java.util.Set.of("params", "data"), input.keySet());
    var failure = assertThrows(BusinessException.class, input::data);
    assertSame(failure, assertThrows(BusinessException.class, input::data));
    assertEquals(Map.of("id", "one"), input.params());
  }

  private static BusinessActionHttpInputPlan currentPlan(Map<String, String> mapping) {
    return new BusinessActionHttpInputPlan(SimpleWebAPIModel.builder().post("/orders")
        .extraProp("formatVersion", "2.0").extraProp("actionInputMapping", mapping).build());
  }

  private static ServerRequest request(String body, String query) {
    return serverRequest(servlet(body, query));
  }

  private static MockHttpServletRequest servlet(String body, String query) {
    var request = new MockHttpServletRequest("POST", "/orders");
    request.setQueryString(query);
    request.setContentType("application/json");
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  private static ServerRequest serverRequest(MockHttpServletRequest request) {
    return ServerRequest.create(request, List.of(new MappingJackson2HttpMessageConverter(Json.mapperCopy())));
  }
}
