package com.ouroboros.businessmodel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.authority.BehaviorActionMPS;
import com.ouroboros.businessmodel.projection.authority.CustomMPS;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class BusinessModelExecutionAuthorityTest {
  private static final String MODEL = "authorityCases.Order";

  @Test
  void actionsAndTheirCustomReferencesReuseTheOnePublishedExecutionAuthority() {
    var meta = meta();
    var behavior = resolve(meta, MODEL + ".workflow");
    DefaultBusinessModel model = new DefaultBusinessModel(meta, provider()) {
      @Override public BusinessModelBehavior getBehavior() { return behavior; }
      @Override public List<ActionDefinition> getActions() { return behavior.getActionDefinitions(); }
    };
    var projection = new ModelProjectionPipelineContext<>(model);
    new BehaviorActionMPS().process(projection);
    new CustomMPS().process(projection);
    var authorities = projection.getBundle().getModelMap(Authority.class);
    assertEquals(Set.of(MODEL + ".workflow", MODEL + ".behaviors.inspect"), authorities.keySet());
    var apis = authorities.get(MODEL + ".workflow").getApis();
    assertTrue(apis.contains("POST /api/business-model-behaviors/" + MODEL + "/actions/startProcess/invoke"));
    assertTrue(apis.contains("POST /api/orders/start-approval"));
    assertEquals(MODEL + ".workflow", behavior.executionAuthorityName("STARTPROCESS"));
    assertThrows(UnsupportedOperationException.class, () -> behavior.getExecutionAuthorities().clear());
  }

  @Test
  void exportReusesItsExistingAuthorityAndUrlWithoutCreatingAnActionAlias() {
    var meta = new DefaultBusinessModelMeta(); meta.setNamespace("exportCases"); meta.setName("Order"); meta.setTitle("Order");
    var id = new com.ouroboros.businessmodel.meta.BusinessModelFieldMeta(); id.setName("id"); id.setType("String"); id.setIsPrimaryKey(true);
    meta.setFields(List.of(id)); meta.setExtraProp("features", Map.of("isEnabledServerSideExport", true));
    var model = new DefaultBusinessModel(meta, provider());
    var action = model.getBehavior().getActions().get("export");
    assertTrue(action.getReadOnly()); assertTrue(action.getIsEnabled()); assertTrue(action.getEmits().isEmpty());
    assertEquals("businessModelExportHandler", action.getBinding().getHandlerName());
    assertEquals("binary", action.getOutputSchema().get("format"));
    assertFalse(model.getBehavior().getActions().containsKey("serverSideExport"));
    assertEquals("exportCases.Order.serverSideExport", model.getBehavior().executionAuthorityName("export"));
    var projection = new ModelProjectionPipelineContext<>(model);
    new com.ouroboros.businessmodel.projection.authority.ServerSideExportMPS().process(projection);
    new BehaviorActionMPS().process(projection);
    assertFalse(projection.getModel(Authority.class, "exportCases.Order.export").isPresent());
    var authority = projection.getModel(Authority.class, "exportCases.Order.serverSideExport").orElseThrow();
    assertTrue(authority.getApis().contains("GET /api/default-business-model-data-export/exportCases.Order/export-excel"));
    assertTrue(authority.getApis().contains("POST /api/business-model-behaviors/exportCases.Order/actions/export/invoke"));
    meta.setExtraProp("features", Map.of("isEnabledServerSideExport", false));
    assertFalse(new DefaultBusinessModel(meta, provider()).getBehavior().getActions().containsKey("export"));
  }

  @Test
  void authorityResolutionIsVersionedAndMetadataCannotChooseItsOwnExecutionAuthority() {
    var meta = meta();
    assertNotEquals(resolve(meta, MODEL + ".workflow").getModelRevision(),
        resolve(meta, MODEL + ".startProcess").getModelRevision());
    var document = "{\"modelType\":\"default\",\"namespace\":\"authorityCases\",\"name\":\"Spoof\","
        + "\"actions\":[{\"name\":\"run\",\"executionAuthorityName\":\"authorityCases.Spoof.search\"}]}";
    var parsed = (DefaultBusinessModelMeta) BusinessModelMetaParser.parse(document).get();
    assertEquals("authorityCases.Spoof.run", new DefaultBusinessModel(parsed, provider()).getBehavior().executionAuthorityName("run"));
    assertThrows(IllegalArgumentException.class, () -> resolve(meta, "another.Model.search"));
  }

  private static BusinessModelBehavior resolve(DefaultBusinessModelMeta meta, String authority) {
    var action = new ActionDefinition();
    action.setName("startProcess");
    action.setIsEnabled(true);
    return BusinessModelBehaviorResolver.resolve(MODEL, meta, List.of(),
        new LegacyBusinessActionNormalizer.NormalizedBehavior(List.of(action), List.of(), Map.of(), Map.of("STARTPROCESS", authority)),
        List.of(), List.of());
  }

  private static DefaultBusinessModelMeta meta() {
    return (DefaultBusinessModelMeta) BusinessModelMetaParser.parse(Json.toJsonString(Map.of("formatVersion", "2.0",
        "modelType", "default", "namespace", "authorityCases", "name", "Order", "title", "Order", "custom", List.of(
            Map.of("name", "startApproval", "api", Map.of("isEnabled", true, "implementationMode", "ACTION",
                "action", "startProcess", "url", "/orders/start-approval", "httpMethod", "POST", "actionInputMapping", Map.of())))))).get();
  }

  private static BusinessModelProvider provider() {
    return new BusinessModelProvider() {
      public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
      public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
    };
  }
}
