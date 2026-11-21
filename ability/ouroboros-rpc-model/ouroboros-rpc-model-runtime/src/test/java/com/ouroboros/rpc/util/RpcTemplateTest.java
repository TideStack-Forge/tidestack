package com.ouroboros.rpc.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.expression.ExpressionInputReference;
import com.ouroboros.rpc.http.HttpRPCRequestDefine;
import com.ouroboros.rpc.http.HttpRPCRequestParameterDefine;

import static org.junit.jupiter.api.Assertions.*;

class RpcTemplateTest {
  @Test
  void discoveryAndEvaluationShareBoundariesAndPreserveTypedWholePlaceholders() {
    String template = "#{service.host}/$order.id/${pageNo + 1}/${orders[index].id}";
    var inputs = ExpressionUtils.analyze(template).get();
    assertEquals(List.of("service.host"), inputs.configKeys());
    assertEquals(Set.of("order", "pageNo", "orders", "index"), inputs.variables().stream()
        .map(ExpressionInputReference::root).collect(Collectors.toSet()));
    var values = Map.<String, Object>of("order", Map.of("id", "R001"), "pageNo", 1,
        "orders", List.of(Map.of("id", "R002")), "index", 0, "enabled", false);
    assertEquals("http://localhost/R001/2/R002", ExpressionUtils.eval(template, values, key -> "http://localhost").get());
    assertEquals(false, ExpressionUtils.eval("$enabled", values, key -> null).get());
    assertEquals(2, ExpressionUtils.eval("${pageNo + 1}", values, key -> null).get());
    assertEquals(Map.of("id", "R001"), ExpressionUtils.eval("$order", values, key -> null).get());
    assertNull(ExpressionUtils.eval("${value}", Collections.singletonMap("value", null), key -> null).get());
  }

  @Test
  void substitutionsAreNotRecursivelyEvaluatedAndEscapesQuotesAndNestedBracesArePreserved() {
    assertEquals("${secret}", ExpressionUtils.eval("#{value}", Map.of("secret", "leak"), key -> "${secret}").get());
    assertEquals("${literal}/}", ExpressionUtils.eval("$${literal}/${'}'}", Map.of(), key -> null).get());
    assertEquals(List.of(Map.of("id", 7)), ExpressionUtils.eval("${{{id: 7}}}", Map.of(), key -> null).get());
    assertEquals(List.of(), ExpressionUtils.analyze("$${literal}/${'#{not-a-config}'}").get().configKeys());
    assertEquals("", ExpressionUtils.eval("#{empty}", Map.of(), key -> "").get());
    assertTrue(ExpressionUtils.eval("#{missing}", Map.of(), key -> null).isLeft());
    assertTrue(ExpressionUtils.analyze("${broken").isLeft());
  }

  @Test
  void requestBuildFailsAtUrlOrParameterEvaluationAndKeepsFalseAndZero() {
    var definition = new HttpRPCRequestDefine();
    definition.setMethod("POST"); definition.setUrl("http://localhost/${1 / 0}");
    assertTrue(HttpRpcRequestBuilder.buildRequest(definition, Map.of(), key -> null).isFailure());
    definition.setUrl("http://localhost");
    var parameter = new HttpRPCRequestParameterDefine();
    parameter.setName("value"); parameter.setIn("body"); parameter.setRequired(true); parameter.setValue("${1 / 0}");
    definition.setParameters(List.of(parameter));
    assertTrue(HttpRpcRequestBuilder.buildRequest(definition, Map.of(), key -> null).isFailure());
    for (Object value : Arrays.asList(false, 0)) {
      parameter.setValue("$value");
      var request = HttpRpcRequestBuilder.buildRequest(definition, Map.of("value", value), key -> null).get();
      assertEquals(value, request.getBody().get("value"));
    }
    assertTrue(HttpRpcRequestBuilder.buildRequest(definition, Map.of(), key -> null).isFailure());
  }
}
