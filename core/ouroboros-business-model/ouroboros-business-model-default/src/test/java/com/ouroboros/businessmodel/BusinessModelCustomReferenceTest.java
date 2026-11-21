package com.ouroboros.businessmodel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.authority.CustomMPS;
import com.ouroboros.businessmodel.projection.custom.CustomAPIMPS;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.util.Json;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.WebAPIModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessModelCustomReferenceTest {
  @Test
  void twoFunctionsReferenceOneActionWithoutDuplicatingItsImplementationOrPermission() {
    var document = document();
    document.put("custom", List.of(function("fromList", true, "approve"), function("fromDetail", true, "approve")));
    var model = model(document);
    assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
    assertEquals(Set.of("approve"), model.getBehavior().getActions().keySet());
    var projection = project(model);
    var apis = projection.getBundle().getModelMap(WebAPIModel.class);
    assertEquals(2, apis.size());
    assertTrue(apis.values().stream().allMatch(api -> api.getHandlerType() == HandlerType.BUSINESS_ACTION
        && api.getExtraProp(String.class, "action").orElseThrow().equals("approve")));
    assertTrue(projection.getBundle().getModelMap(FlowModelMeta.class).isEmpty());
    var authorities = projection.getBundle().getModelMap(Authority.class);
    assertEquals(Set.of("referenceCases.Order.approve"), authorities.keySet());
    assertEquals(2, authorities.get("referenceCases.Order.approve").getApis().size());
    assertEquals(document.get("custom"), model.getExtraPropDeep(List.class, "custom").orElseThrow());
  }

  @Test
  void disablingAnEntryDoesNotDisableTheReferencedActionOrAnotherEntry() {
    var document = document();
    document.put("custom", List.of(function("fromList", false, "approve"), function("fromDetail", true, "approve")));
    var model = model(document);
    assertTrue(model.getBehavior().getActions().get("approve").getIsEnabled());
    assertEquals(1, project(model).getBundle().getModelMap(WebAPIModel.class).size());
  }

  @Test
  void ordinaryRestDoesNotAcquireAnActionEvenWhenAnIndependentActionHasTheSameName() {
    var document = document();
    document.put("custom", List.of(Map.of("name", "approve", "api", Map.of(
        "isEnabled", true, "implementationMode", "WEB_API", "url", "approve", "httpMethod", "POST"))));
    var model = model(document);
    assertTrue(model.getValidationResult().isValid(), model.getValidationResult().getErrors().toString());
    assertEquals(1, model.getActions().size());
    assertTrue(project(model).getBundle().getModelMap(WebAPIModel.class).isEmpty());
  }

  @Test
  void rejectsMissingReferencesAndInlineDefinitionsInNewDocuments() {
    for (String name : List.of("", "missing")) {
      var document = document();
      document.put("custom", List.of(function("fromList", true, name)));
      assertFalse(model(document).getValidationResult().isValid());
    }
    for (String key : List.of("isBindAction", "isGenerateImplementation", "isCustomLogicFlow", "script", "inputSchema", "emits")) {
      var document = document();
      var api = new LinkedHashMap<String, Object>(Map.of("isEnabled", true, "implementationMode", "ACTION", "action", "approve"));
      api.put(key, false);
      document.put("custom", List.of(Map.of("name", "fromList", "api", api)));
      var validation = model(document).getValidationResult();
      assertFalse(validation.isValid(), key);
      assertTrue(validation.getErrors().toString().contains("custom.fromList.api." + key), validation.getErrors().toString());
    }
  }

  @Test
  void legacyDocumentsKeepTheirOriginalFormatAndRejectNewReferenceSyntax() {
    var document = document();
    document.remove("formatVersion");
    document.put("custom", List.of(function("fromList", true, "approve")));
    assertTrue(model(document).getValidationResult().getErrors().toString().contains("reference-requires-formatVersion-2.0"));
    document.put("custom", List.of(Map.of("name", "plainRest", "api", Map.of("isEnabled", true, "isBindAction", false))));
    assertTrue(project(model(document)).getBundle().getModelMap(WebAPIModel.class).isEmpty());
  }

  private static Map<String, Object> function(String name, boolean enabled, String action) {
    return Map.of("name", name, "api", Map.of("isEnabled", enabled, "implementationMode", "ACTION",
        "action", action, "httpMethod", "POST", "url", name, "actionInputMapping", Map.of()));
  }

  private static Map<String, Object> document() {
    return new LinkedHashMap<>(Map.of("formatVersion", "2.0", "modelType", "default", "namespace", "referenceCases", "name", "Order",
        "title", "Order", "isEnabled", true, "actions", List.of(Map.of("name", "approve", "title", "Approve", "isEnabled", true,
            "binding", Map.of("kind", "handler", "handlerName", "approve-handler")))));
  }

  private static DefaultBusinessModel model(Map<String, Object> document) {
    var meta = (DefaultBusinessModelMeta) BusinessModelMetaParser.parse(Json.toJsonString(document)).get();
    return new DefaultBusinessModel(meta, new BusinessModelProvider() {
      @Override
      public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }

      @Override
      public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
    });
  }

  private static ModelProjectionPipelineContext<DefaultBusinessModel> project(DefaultBusinessModel model) {
    var context = new ModelProjectionPipelineContext<>(model);
    new CustomAPIMPS().process(context);
    new CustomMPS().process(context);
    return context;
  }
}
