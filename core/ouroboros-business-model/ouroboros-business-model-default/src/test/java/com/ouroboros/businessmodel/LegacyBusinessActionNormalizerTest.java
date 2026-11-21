package com.ouroboros.businessmodel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class LegacyBusinessActionNormalizerTest {
  private static final BusinessModelProvider EMPTY = new BusinessModelProvider() {
    public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
    public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
  };

  @Test
  void legacyGeneratedCustomApisDefaultToRequestParamsAndBodyWithoutChangingSourceMetadata() {
    for (String version : java.util.Arrays.asList(null, "0.1", "1.0")) {
      var meta = new DefaultBusinessModelMeta();
      meta.setNamespace("legacyContract");
      meta.setName("Order");
      meta.setFormatVersion(version);
      meta.setExtraProp("custom", List.of(Map.of("name", "batchAdd", "api", Map.of(
          "isEnabled", true, "isGenerateImplementation", true, "httpMethod", "POST",
          "url", "batch-add", "scriptLanguage", "groovy", "script", "return request.body"))));
      String original = Json.toJsonString(meta);
      var model = new DefaultBusinessModel(meta, EMPTY);
      var validation = new com.ouroboros.businessmodel.validation.ValidationContext<>(model);

      new com.ouroboros.businessmodel.validation.BehaviorDefinitionValidator().doValidate(validation);

      assertTrue(validation.getErrors().isEmpty(), () -> "legacy format " + version + ": " + validation.getErrors());
      var projection = new com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext<>(model);
      new com.ouroboros.businessmodel.projection.custom.CustomAPIMPS().process(projection);
      var api = projection.getModelMap(com.ouroboros.webapi.WebAPIModel.class).values().stream().findFirst().orElseThrow();
      assertEquals(Map.of("params", "${ request.params }", "data", "${ request.body }"),
          api.getExtraProp(Map.class, "actionInputMapping").orElseThrow());
      assertEquals(original, Json.toJsonString(meta));
    }
  }

  @Test
  void explicitInvalidMappingsAndHighCodeBindingsDoNotAcquireLegacyDefaults() {
    for (var api : List.of(Map.of("isEnabled", true, "isGenerateImplementation", true, "actionInputMapping", "invalid"),
        Map.of("isEnabled", true, "isBindAction", true))) {
      var meta = new DefaultBusinessModelMeta();
      meta.setNamespace("legacyContract"); meta.setName("Order");
      meta.setExtraProp("custom", List.of(Map.of("name", "batchAdd", "api", api)));
      var model = new DefaultBusinessModel(meta, EMPTY);
      var validation = new com.ouroboros.businessmodel.validation.ValidationContext<>(model);

      new com.ouroboros.businessmodel.validation.BehaviorDefinitionValidator().doValidate(validation);

      assertTrue(validation.getErrors().toString().contains("actions.batchAdd.api.actionInputMapping-required"));
    }
  }

  @Test
  void inlineActionRetainsEachContractPropertyFromItsOwningSource() {
    var meta = meta();
    Map<String, Object> condition = Map.of("expression", "${ input.data.confirmed == true }", "message", "请确认");
    Map<String, Object> input = Map.of("type", "object", "properties", Map.of("data", Map.of("type", "object")));
    Map<String, Object> output = Map.of("type", "string");
    var function = new LinkedHashMap<String, Object>(Map.of("name", "approve", "title", "审批", "description", "确认审批",
        "inputSchema", input, "readOnly", false,
        "api", Map.of("isEnabled", true, "isGenerateImplementation", true, "actionInputMapping", Map.of(),
            "outputSchema", output, "readOnly", true, "precondition", condition,
            "emits", List.of(Map.of("modelFullName", meta.getFullName(), "eventName", "approved")))));
    meta.setExtraProp("custom", List.of(function));
    String original = Json.toJsonString(meta);
    var model = new DefaultBusinessModel(meta, EMPTY);
    var action = model.getBehavior().getActions().get("approve");
    assertEquals(input, action.getInputSchema());
    assertEquals(output, action.getOutputSchema());
    assertEquals(condition, action.getPrecondition());
    assertTrue(action.getReadOnly());
    assertEquals("approved", action.getEmits().getFirst().getEventName());
    assertEquals("确认审批", action.getDescription());
    assertEquals("legacyContract.Order.approve", action.getBinding().getFlowName());
    assertEquals(original, Json.toJsonString(meta));
  }

  @Test
  void rootDeclarationsArePreservedAndInvalidInlineConditionsAreDiagnosed() {
    var meta = meta();
    meta.setExtraProp("custom", List.of(Map.of("name", "approve", "precondition", Map.of("expression", "${ request != null }"),
        "emits", List.of(Map.of("modelFullName", meta.getFullName(), "eventName", "approved")),
        "api", Map.of("isEnabled", true, "isGenerateImplementation", true, "actionInputMapping", Map.of()))));
    var model = new DefaultBusinessModel(meta, EMPTY);
    assertEquals(1, model.getBehavior().getActions().get("approve").getEmits().size());
    assertTrue(model.getValidationResult().getErrors().toString().contains("actions.approve.precondition-invalid"));
  }

  @Test
  void unsupportedInlineAuthorizationIsRejectedInsteadOfBeingIgnored() {
    var meta = meta();
    meta.setExtraProp("custom", List.of(Map.of("name", "approve", "api", Map.of("isEnabled", true,
        "isGenerateImplementation", true, "actionInputMapping", Map.of(), "authorization", Map.of("script", "true")))));
    var model = new DefaultBusinessModel(meta, EMPTY);
    assertTrue(model.getValidationResult().getErrors().toString().contains("actions.approve.authorization-unsupported-use-authority"));
  }

  private static DefaultBusinessModelMeta meta() {
    var meta = new DefaultBusinessModelMeta(); meta.setNamespace("legacyContract"); meta.setName("Order");
    var event = new EventDefinition(); event.setName("approved"); event.setSubject("model");
    event.setAllowedProducerKinds(List.of("action")); meta.setEvents(List.of(event));
    return meta;
  }
}
