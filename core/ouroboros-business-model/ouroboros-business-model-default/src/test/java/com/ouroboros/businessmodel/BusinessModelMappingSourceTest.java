package com.ouroboros.businessmodel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.action.BusinessActionInput;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class BusinessModelMappingSourceTest {
  @Test
  void sourceContributionSyntaxDoesNotFollowTheTargetModelVersion() {
    var legacy = model("Legacy", null);
    var current = model("Current", "2.0");
    assertEquals(3, mapped(legacy, "fromCode").get("data"));
    assertEquals("${ 1 + 2 }", mapped(current, "fromCode").get("data"));
    assertEquals("${ 1 + 2 }", mapped(legacy, "fromMetadata").get("data"));
    assertEquals(3, mapped(current, "fromMetadata").get("data"));
    assertEquals("2.0", legacy.getBehavior().getActionSources().get("fromCode").formatVersion());
    assertNull(current.getBehavior().getActionSources().get("fromCode").formatVersion());
    assertEquals("test-source.java", legacy.getBehavior().getActionSources().get("fromCode").resource());
  }

  @Test
  void owningVersionChangesThePublishedRevisionAndKeepsTheOriginalDefinition() {
    var legacy = model("Current", null);
    var current = model("Current", "2.0");
    assertNotEquals(legacy.getBehavior().getModelRevision(), current.getBehavior().getModelRevision());
    assertEquals("${ 1 + 2 }", current.getActions().stream().filter(action -> action.getName().equals("fromMetadata"))
        .findFirst().orElseThrow().getBinding().getInputMapping().get("data"));
  }

  private static Map<String, Object> mapped(DefaultBusinessModel model, String action) {
    var plan = model.getBehavior().inputPlan(action);
    return plan.flowInput(plan.prepare(BusinessActionInput.empty()), Map.of());
  }

  private static DefaultBusinessModel model(String name, String version) {
    Map<String, Object> source = new LinkedHashMap<>(Map.of("modelType", "default", "namespace", "sourceCases",
        "name", name, "title", name, "isEnabled", true, "actions", List.of(Map.of("name", "fromMetadata",
            "isEnabled", true, "binding", Map.of("kind", "logicflow", "flowName", "sourceCases.flow",
                "inputMapping", Map.of("data", "${ 1 + 2 }"))))));
    if (version != null) { source.put("formatVersion", version); }
    var meta = (DefaultBusinessModelMeta) BusinessModelMetaParser.parse(Json.toJsonString(source)).get();
    return new DefaultBusinessModel(meta, new BusinessModelProvider() {
      @Override public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
      @Override public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
    });
  }
}
