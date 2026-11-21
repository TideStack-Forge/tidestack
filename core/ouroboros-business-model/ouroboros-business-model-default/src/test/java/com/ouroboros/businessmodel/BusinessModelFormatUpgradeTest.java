package com.ouroboros.businessmodel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource;
import com.ouroboros.businessmodel.behavior.BusinessBehaviorMappingPlan;
import com.ouroboros.businessmodel.behavior.EventSubscriptionDefinition;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class BusinessModelFormatUpgradeTest {
  private static final BusinessModelProvider EMPTY = new BusinessModelProvider() {
    public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
    public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
  };

  @Test
  void extractsInlineScriptsIntoFormalFlowsWhilePreservingRoutesContractAndSource() {
    var original = meta();
    String script = "return request.params.id + ':' + request.body.note";
    var api = new LinkedHashMap<String, Object>(Map.of("isEnabled", true, "isGenerateImplementation", true,
        "httpMethod", "POST", "url", "/original/{id}", "scriptLanguage", "groovy", "script", script,
        "actionInputMapping", Map.of("params.id", "$.pathVariables.id", "data", "$.body"),
        "readOnly", true, "precondition", Map.of("expression", "${ input.params.id != null }")));
    original.setExtraProp("custom", List.of(Map.of("name", "approve", "title", "确认", "api", api,
        "ui", Map.of("isEnabled", true, "mode", "api", "buttonLevel", "primary"))));
    original.setExtraProp("future", Map.of("keep", "原文"));
    String before = Json.toJsonString(original);
    var plan = new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY);
    assertEquals(before, Json.toJsonString(original));
    assertEquals("2.0", plan.model().getFormatVersion());
    var action = plan.model().getActions().getFirst();
    assertEquals("approve", action.getName());
    assertEquals("formatUpgrade.Order.approve", action.getBinding().getFlowName());
    assertTrue(action.getReadOnly());
    assertEquals(api.get("precondition"), action.getPrecondition());
    var upgradedApi = customApi(plan.model(), 0);
    assertEquals("ACTION", upgradedApi.get("implementationMode"));
    assertEquals("approve", upgradedApi.get("action"));
    assertEquals("/original/{id}", upgradedApi.get("url"));
    assertEquals("POST", upgradedApi.get("httpMethod"));
    assertFalse(upgradedApi.containsKey("script"));
    assertFalse(upgradedApi.containsKey("readOnly"));
    var mapping = (Map<?, ?>) upgradedApi.get("actionInputMapping");
    assertEquals("${ request['pathVariables']['id'] }", mapping.get("params.id"));
    assertEquals("${ request['body'] }", mapping.get("data"));
    assertEquals(Map.of("keep", "原文"), plan.model().getExtraProp("future").orElseThrow());
    FlowModelMeta flow = plan.scriptFlows().getFirst();
    assertEquals("formatUpgrade.Order.approve", flow.getName());
    var scriptNode = flow.getNodes().stream().filter(node -> "script".equals(node.getType())).findFirst().orElseThrow();
    assertEquals(script, scriptNode.getExtraProps().get("script"));
    assertEquals("request", scriptNode.getInputPorts().getFirst().getName());
    assertEquals("groovy", scriptNode.getExtraProps().get("language"));
    var oldModel = new DefaultBusinessModel(original, EMPTY);
    var newModel = new DefaultBusinessModel(plan.model(), EMPTY);
    assertEquals(oldModel.getBehavior().executionAuthorityName("approve"), newModel.getBehavior().executionAuthorityName("approve"));
    var legacyFlow = oldModel.buildModelBundle().getModel(FlowModelMeta.class, flow.getName()).orElseThrow();
    var legacyScript = legacyFlow.getNodes().stream().filter(node -> "script".equals(node.getType())).findFirst().orElseThrow();
    assertEquals("/custom/0/api/script", legacyScript.getExtraProps().get("codeSourcePointer"));
    assertEquals("/script", scriptNode.getExtraProps().get("codeSourcePointer"));
    // Extracted scripts belong to their own document; all execution metadata stays equivalent.
    var expected = Json.toMap(Json.toJsonString(legacyFlow));
    expected.put("nodes", legacyFlow.getNodes().stream().map(node -> {
      var serialized = Json.toMap(Json.toJsonString(node));
      if (node == legacyScript) { serialized.put("codeSourcePointer", "/script"); }
      return serialized;
    }).toList());
    assertEquals(expected, Json.toMap(Json.toJsonString(flow)));
    assertTrue(newModel.getValidationResult().isValid(), newModel.getValidationResult().getErrors().toString());
  }

  @Test
  void legacyGeneratedApisWithoutMappingsUpgradeWithTheSameDefaultInputContract() {
    var original = meta();
    original.setExtraProp("custom", List.of(Map.of("name", "batchAdd", "api", Map.of(
        "isEnabled", true, "isGenerateImplementation", true, "httpMethod", "POST", "url", "batch-add"))));
    String before = Json.toJsonString(original);

    var plan = new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY);

    assertEquals(Map.of("params", "${ request.params }", "data", "${ request.body }"),
        customApi(plan.model(), 0).get("actionInputMapping"));
    assertTrue(new DefaultBusinessModel(plan.model(), EMPTY).getValidationResult().isValid());
    assertEquals(before, Json.toJsonString(original));
  }

  @Test
  void disabledScriptsAreMaterializedWithoutEnablingTheirActionsOrHttpEntries() {
    var original = meta();
    original.setExtraProp("custom", List.of(Map.of("name", "archive", "api", Map.of(
        "isEnabled", false, "isGenerateImplementation", true, "script", "return 'keep disabled script'"))));
    var plan = new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY);
    assertFalse(plan.model().getActions().getFirst().getIsEnabled());
    assertEquals(false, customApi(plan.model(), 0).get("isEnabled"));
    assertEquals(1, plan.scriptFlows().size());
    assertFalse(new DefaultBusinessModel(plan.model(), EMPTY).getBehavior().getActions().get("archive").getIsEnabled());
    assertFalse(new DefaultBusinessModel(original, EMPTY).getBehavior().getActions().containsKey("archive"));
  }

  @Test
  void existingFlowNamesAndHighCodeBindingResponsibilityRemainAtTheirOriginalSources() {
    var original = meta();
    original.setExtraProp("custom", List.of(Map.of("name", "customFlow", "api", Map.of("isEnabled", true,
        "isGenerateImplementation", true, "isCustomLogicFlow", true, "actionInputMapping", Map.of())),
        Map.of("name", "highCode", "api", Map.of("isEnabled", true, "isBindAction", true, "actionInputMapping", Map.of()))));
    var plan = new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY);
    assertTrue(plan.scriptFlows().isEmpty());
    assertEquals("formatUpgrade.Order.customFlow", plan.model().getActions().getFirst().getBinding().getFlowName());
    assertNull(plan.model().getActions().get(1).getBinding());
    assertEquals("highCode", customApi(plan.model(), 1).get("action"));
  }

  @Test
  void ordinaryRestAndPureUiDoNotAcquireActionsOrWorkflows() {
    var original = meta();
    var uiOnly = Map.of("name", "help", "ui", Map.of("isEnabled", true, "mode", "dialog"));
    original.setExtraProp("custom", List.of(Map.of("name", "print", "api", Map.of("isEnabled", true,
        "isGenerateImplementation", false, "isBindAction", false, "url", "print", "httpMethod", "GET")), uiOnly));
    var plan = new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY);
    assertTrue(plan.model().getActions().isEmpty());
    assertTrue(plan.scriptFlows().isEmpty());
    assertEquals("WEB_API", customApi(plan.model(), 0).get("implementationMode"));
    assertEquals(uiOnly, plan.model().getExtraProp(List.class, "custom").orElseThrow().get(1));
  }

  @Test
  void conflictingNamesDormantRestScriptsAndMixedFormatsAreRejectedWithoutChangingTheOriginal() {
    var original = meta();
    var action = new ActionDefinition(); action.setName("APPROVE"); original.setActions(List.of(action));
    original.setExtraProp("custom", List.of(Map.of("name", "approve", "api", Map.of("isGenerateImplementation", true))));
    String before = Json.toJsonString(original);
    assertThrows(BusinessException.class, () -> new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY));
    assertEquals(before, Json.toJsonString(original));
    original.setActions(List.of());
    original.setExtraProp("custom", List.of(Map.of("name", "rest", "api", Map.of("script", "must not lose"))));
    assertThrows(BusinessException.class, () -> new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY));
    original.setExtraProp("custom", List.of(Map.of("name", "mixed", "api", Map.of("implementationMode", "ACTION", "action", "approve"))));
    assertThrows(BusinessException.class, () -> new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY));
    original.setFormatVersion("2.0");
    assertThrows(BusinessException.class, () -> new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY));
  }

  @Test
  void actionMappingsConvertWhileSubscriptionElAndLiteralOutputsRemainUnchanged() {
    var original = meta();
    original.setFormatVersion("1.0");
    var action = new ActionDefinition(); action.setName("lookup");
    var binding = new ActionBinding(); binding.setKind("logicflow"); binding.setFlowName("existing.lookup");
    binding.setInputMapping(Map.of("data", "$.input.data"));
    binding.setOutputMapping(Map.of("template", "${ doNotEvaluate }")); action.setBinding(binding);
    action.setInputSchema(Map.of("type", "object", "properties", Map.of("data", Map.of("type", "object"))));
    original.setActions(List.of(action));
    var subscription = new EventSubscriptionDefinition(); subscription.setExecutionMode(com.ouroboros.businessmodel.event.BusinessEventExecutionMode.AFTER_COMMIT_TRANSACTIONAL); subscription.setName("afterApproved");
    subscription.getTarget().setInputMapping(Map.of("data", "${ event.payload }")); original.setEventSubscriptions(List.of(subscription));
    var plan = new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY);
    var upgradedBinding = plan.model().getActions().getFirst().getBinding();
    var source = new BehaviorDefinitionSource(original.getFullName(), "actions.lookup.binding", "2.0");
    assertEquals(Map.of("data", Map.of("value", "ok")), BusinessBehaviorMappingPlan.flowInput(upgradedBinding.getInputMapping(),
        action.getInputSchema(), source).map(Map.of("input", Map.of("data", Map.of("value", "ok"))), value -> value));
    assertEquals(binding.getOutputMapping(), BusinessBehaviorMappingPlan.flowOutput(upgradedBinding.getOutputMapping(), source).evaluate(Map.of()));
    assertEquals("${ event.payload }", subscription.getTarget().getInputMapping().get("data"));
    assertEquals(subscription.getTarget().getInputMapping(), plan.model().getEventSubscriptions().getFirst().getTarget().getInputMapping());
    assertTrue(plan.scriptFlows().isEmpty());
  }

  @Test
  void codeBindingsRemainContributionsAfterUpgradeAndAreNeverCopiedIntoMetadata() {
    var original = meta(); original.setNamespace("sales"); original.setName("CodeBoundOrder");
    original.setExtraProp("custom", List.of(Map.of("name", "approve", "api", Map.of("isEnabled", true,
        "isBindAction", true, "actionInputMapping", Map.of()))));
    var plan = new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY);
    assertNull(plan.model().getActions().getFirst().getBinding());
    var published = new DefaultBusinessModel(plan.model(), EMPTY);
    assertEquals("custom-api-handler", published.getBehavior().getActions().get("approve").getBinding().getHandlerName());
    assertTrue(published.getValidationResult().isValid(), published.getValidationResult().getErrors().toString());
    assertNull(plan.model().getActions().getFirst().getBinding());
  }

  @Test
  void unsupportedLegacyDefinitionsAreLocatedWithoutMutatingTheOriginal() {
    for (Object definition : List.of("invalid", Map.of("name", "", "api", Map.of()),
        Map.of("name", "approve", "api", Map.of("isBindAction", true, "isEnabled", true)),
        Map.of("name", "approve", "binding", Map.of("kind", "handler"), "api", Map.of("isBindAction", true)),
        Map.of("name", "rest", "api", Map.of("precondition", Map.of("expression", "${ true }"))))) {
      var original = meta(); original.setExtraProp("custom", List.of(definition));
      String before = Json.toJsonString(original);
      assertThrows(BusinessException.class, () -> new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY));
      assertEquals(before, Json.toJsonString(original));
    }
    var original = meta(); original.setModelType("dashboard");
    assertThrows(BusinessException.class, () -> new DefaultBusinessModelFormatUpgrade().prepare(original, EMPTY));
  }

  private static DefaultBusinessModelMeta meta() {
    var meta = new DefaultBusinessModelMeta(); meta.setNamespace("formatUpgrade"); meta.setName("Order");
    meta.setTitle("订单"); meta.setIsEnabled(true); return meta;
  }

  private static Map<?, ?> customApi(DefaultBusinessModelMeta meta, int index) {
    var functions = meta.getExtraProp(List.class, "custom").orElseThrow();
    return (Map<?, ?>) ((Map<?, ?>) functions.get(index)).get("api");
  }
}
