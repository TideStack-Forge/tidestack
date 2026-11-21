package com.ouroboros.businessmodel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class BusinessModelScriptActionTest {
  private static final BusinessModelProvider EMPTY = new BusinessModelProvider() {
    public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
    public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
  };

  @Test
  void createsOneFormalActionAndFlowAndOnlyReplacesTheSelectedFunctionReference() {
    var original = model();
    String before = Json.toJsonString(original);
    var plan = new DefaultBusinessModelScriptAction().prepare(original, EMPTY, "button", "calculate", "groovy", "return $input.data", null);
    assertEquals(before, Json.toJsonString(original));
    assertEquals(1, plan.model().getActions().size());
    var action = plan.model().getActions().getFirst();
    assertEquals("calculate", action.getName()); assertTrue(action.getReadOnly()); assertTrue(action.getIsEnabled());
    assertEquals("logicflow", action.getBinding().getKind()); assertEquals("scripts.Order.calculate", action.getBinding().getFlowName());
    assertFalse(action.getBinding().isRequiresHttpRequest()); assertTrue(action.getBinding().getInputMapping().isEmpty());
    var functions = plan.model().getExtraProp(List.class, "custom").orElseThrow();
    assertEquals("calculate", ((Map<?, ?>) ((Map<?, ?>) functions.getFirst()).get("api")).get("action"));
    assertEquals(Map.of("params", "${ request.params }", "data", "${ request.body }"),
        ((Map<?, ?>) ((Map<?, ?>) functions.getFirst()).get("api")).get("actionInputMapping"));
    assertEquals(((List<?>) original.getExtraProp("custom").orElseThrow()).get(1), functions.get(1));
    assertEquals(Map.of("keep", true), plan.model().getExtraProp("future").orElseThrow());
    var script = plan.flow().getNodes().stream().filter(node -> "script".equals(node.getType())).findFirst().orElseThrow();
    assertEquals("input", script.getInputPorts().getFirst().getName());
    assertEquals("return $input.data", script.getExtraProps().get("script"));
    assertEquals("groovy", script.getExtraProps().get("language"));
    assertTrue(new DefaultBusinessModel(plan.model(), EMPTY).getValidationResult().isValid());
    var input = new com.ouroboros.businessmodel.action.BusinessActionInputPlan(action.getInputSchema());
    var httpRequest = Map.<String, Object>of("headers", Map.of("x-trace", "trace"));
    var prepared = input.prepare(com.ouroboros.businessmodel.action.BusinessActionInput.of(Map.of("id", 1), List.of("data")));
    var flowInput = input.flowInput(prepared, Map.of("request", httpRequest));
    assertEquals(Map.of("id", 1), flowInput.get("params")); assertEquals(List.of("data"), flowInput.get("data"));
    assertSame(httpRequest, ((Map<?, ?>) flowInput.get("context")).get("request"));
  }

  @Test
  void existingMergedActionsAreReusedRatherThanDuplicatedAndWritingRequiresAnExplicitChoice() {
    var original = model();
    var created = new DefaultBusinessModelScriptAction().prepare(original, EMPTY, "button", "calculate", "groovy", "return $input.data", false);
    assertFalse(created.model().getActions().getFirst().getReadOnly());
    assertThrows(BusinessException.class, () -> new DefaultBusinessModelScriptAction().prepare(created.model(), EMPTY,
        "button", "CALCULATE", "groovy", "return 1", false));
  }

  @Test
  void rejectsLegacyOrAmbiguousFunctionsInvalidNamesAndIncompleteImplementationsWithoutMutatingTheDraft() {
    var original = model();
    String before = Json.toJsonString(original);
    assertThrows(BusinessException.class, () -> new DefaultBusinessModelScriptAction().prepare(original, EMPTY, "missing", "newAction", "groovy", "return 1", true));
    for (String name : List.of("", "../escape", "has.dot")) {
      assertThrows(BusinessException.class, () -> new DefaultBusinessModelScriptAction().prepare(original, EMPTY, "button", name, "groovy", "return 1", true));
    }
    assertThrows(BusinessException.class, () -> new DefaultBusinessModelScriptAction().prepare(original, EMPTY, "button", "newAction", "", "return 1", true));
    assertThrows(BusinessException.class, () -> new DefaultBusinessModelScriptAction().prepare(original, EMPTY, "button", "newAction", "groovy", " ", true));
    assertThrows(BusinessException.class, () -> new DefaultBusinessModelScriptAction().prepare(original, EMPTY, "rest", "newAction", "groovy", "return 1", true));
    assertEquals(before, Json.toJsonString(original));
    original.setFormatVersion(null);
    assertThrows(BusinessException.class, () -> new DefaultBusinessModelScriptAction().prepare(original, EMPTY, "button", "newAction", "groovy", "return 1", true));
  }

  private DefaultBusinessModelMeta model() {
    return (DefaultBusinessModelMeta) BusinessModelMetaParser.parse("""
        {"modelType":"default","formatVersion":"2.0","namespace":"scripts","name":"Order",
         "future":{"keep":true},"custom":[
          {"name":"button","api":{"isEnabled":true,"httpMethod":"POST","url":"button","implementationMode":"ACTION","future":1}},
          {"name":"rest","api":{"isEnabled":false,"implementationMode":"WEB_API"}}]}
        """).get();
  }
}
