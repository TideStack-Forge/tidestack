package com.ouroboros.businessmodel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.behavior.BehaviorDefinitionSource;
import com.ouroboros.businessmodel.behavior.BehaviorMemberSource;
import com.ouroboros.businessmodel.behavior.BusinessBehaviorCatalog;
import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class BusinessModelBehaviorSourceTest {
  @Test
  void writeEffectsFollowTheSameFeaturePlansIncludingUserActionsAndDeleteStrategyPrecedence() {
    var meta = parse("""
        {"modelType":"default","namespace":"origin","name":"Effects","fields":[{"name":"id","type":"单行文本","isPrimaryKey":true}],
         "features":{"isEnabledSoftDelete":true,"isEnabledAuditFields":true},"enableScd2History":true,
         "actions":[{"name":"customWrite","readOnly":false},{"name":"customRead","readOnly":true}]}
        """);
    var original = Json.toJsonString(meta);
    var model = new DefaultBusinessModel.Builder().build(meta, emptyProvider());
    var catalog = BusinessBehaviorCatalog.from(model.getBehavior());
    var effects = catalog.sources().stream().filter(source -> "effect".equals(source.role())).toList();
    assertEquals(3, effects.stream().filter(source -> "customWrite".equals(source.memberName())).count());
    assertTrue(effects.stream().noneMatch(source -> "customRead".equals(source.memberName()) || "search".equals(source.memberName())));
    assertTrue(effects.stream().allMatch(source -> source.description() != null && !source.description().isBlank()));
    assertEquals(original, Json.toJsonString(meta));
    assertEquals(model.getBehavior().getModelRevision(), new DefaultBusinessModel(model).getBehavior().getModelRevision());
    meta.setEnableArchiveDelete(true);
    var archived = new DefaultBusinessModel(meta, emptyProvider()).getBehavior();
    var paths = archived.getMemberSources().stream().filter(source -> "effect".equals(source.role())
        && "customWrite".equals(source.memberName())).map(source -> source.source().fieldPath()).toList();
    assertTrue(paths.contains("enableArchiveDelete"));
    assertFalse(paths.contains("features.isEnabledSoftDelete"));
    meta.setEnableArchiveDelete(false); meta.setEnableScd2History(false); meta.setExtraProp("features", Map.of());
    assertTrue(new DefaultBusinessModel(meta, emptyProvider()).getBehavior().getMemberSources().stream()
        .noneMatch(source -> "effect".equals(source.role())));
  }

  @Test
  void workflowProducerOriginsPointToTheBoundDefinitionWithoutInventingAnActionEmit() {
    var meta = parse("""
        {"modelType":"default","namespace":"origin","name":"Workflow","workflow":{"isEnabled":false,
         "bindings":[{"processId":"approval"}]}}
        """);
    var behavior = new DefaultBusinessModel(meta, emptyProvider()).getBehavior();
    assertTrue(behavior.getActions().isEmpty());
    var producers = behavior.getMemberSources().stream().filter(source -> "producer".equals(source.role())).toList();
    assertEquals(11, producers.size());
    assertTrue(producers.stream().allMatch(source -> "approval-workflow-definition:approval".equals(source.source().resource())));
    assertTrue(producers.stream().allMatch(source -> source.description().contains("可靠投影")));
  }

  @Test
  void generatedOriginsPointAtTheConfigurationThatActuallyOwnsTheCapability() {
    var meta = parse("""
        {"modelType":"default","namespace":"origin","name":"Document","isEnabled":true,
         "fields":[{"name":"id","type":"单行文本","isPrimaryKey":true}],
         "apis":{"add":{"isEnabled":true}},
         "features":{"isEnabledBatchDelete":true,"isEnabledServerSideExport":true,"isEnabledSoftDelete":true},
         "enableScd2History":true,
         "workflow":{"isEnabled":true,"bindings":[{"processId":"approval"}]},
         "custom":[{"name":"confirm","api":{"isEnabled":true,"isGenerateImplementation":true}}]}
        """);
    String original = Json.toJsonString(meta);
    var behavior = new DefaultBusinessModel(meta, emptyProvider()).getBehavior();
    for (var entry : Map.of("add", "apis.add", "batchDelete", "features.isEnabledBatchDelete",
        "export", "features.isEnabledServerSideExport", "searchDeleted", "features.isEnabledSoftDelete",
        "searchHistory", "enableScd2History", "startProcess", "workflow").entrySet()) {
      assertSource(behavior, "action", entry.getKey(), "generated", entry.getValue());
    }
    assertSource(behavior, "event", "recordsCreated", "generated", "fields");
    assertSource(behavior, "event", "workflowApproved", "generated", "workflow.bindings");
    assertSource(behavior, "action", "confirm", "legacy", "custom.confirm.api");
    assertEquals(original, Json.toJsonString(meta));
    assertThrows(UnsupportedOperationException.class, () -> behavior.getMemberSources().clear());
    assertEquals(behavior.getMemberSources(), behavior.withFeatureQueryPlans(Map.of()).getMemberSources());
    var catalog = BusinessBehaviorCatalog.from(behavior);
    assertEquals(behavior.getModelRevision(), catalog.revision());
    assertEquals(behavior.mutationEventPlan("add"), catalog.mutationEventPlans().get("add"));
    assertTrue(catalog.mutationEventPlans().get("add").eventNames().containsValue("recordsCreated"));
    assertNull(catalog.mutationEventPlans().get("startProcess"));
    catalog.actions().getFirst().setTitle("client changed");
    assertNotEquals("client changed", behavior.getActionDefinitions().getFirst().getTitle());
    var json = Json.toMap(Json.toJsonString(catalog));
    assertFalse(json.containsKey("inputPlans"));
    assertFalse(json.containsKey("preconditions"));
  }

  @Test
  void highCodeBindingDoesNotReplaceTheMetadataDefinitionOrigin() {
    var meta = parse("""
        {"modelType":"default","namespace":"sales","name":"AnnotatedOrder",
         "actions":[{"name":"submit","isEnabled":true}]}
        """);
    var behavior = new DefaultBusinessModel(meta, emptyProvider()).getBehavior();
    assertSource(behavior, "action", "submit", "metadata", "actions.submit");
    assertEquals("test annotation binding", behavior.getActionSources().get("submit").resource());
    var bindings = behavior.getMemberSources().stream().filter(source -> "binding".equals(source.role())).toList();
    assertEquals(1, bindings.size());
    assertEquals("test annotation binding", bindings.getFirst().source().resource());
    assertEquals("actions.submit.binding", bindings.getFirst().source().fieldPath());
  }

  @Test
  void conflictsKeepEveryDefinitionSourceAndEveryDefinitionInsteadOfJustTheIndexedWinner() {
    var meta = parse("""
        {"modelType":"default","namespace":"sales","name":"Order",
         "actions":[{"name":"reserveStock","isEnabled":false}]}
        """);
    var behavior = new DefaultBusinessModel(meta, emptyProvider()).getBehavior();
    var catalog = BusinessBehaviorCatalog.from(behavior);
    assertEquals(2, catalog.actions().stream().filter(action -> "reserveStock".equals(action.getName())).count());
    var sources = catalog.sources().stream().filter(source -> "reserveStock".equals(source.memberName())).toList();
    assertEquals(2, sources.size());
    assertEquals(List.of("metadata", "contribution"), sources.stream().map(BehaviorMemberSource::kind).toList());
    assertFalse(catalog.conflicts().isEmpty());
  }

  @Test
  void provenanceParticipatesInRevisionWithoutChangingMappingOwnershipOrRuntimeContract() {
    var meta = parse("{\"modelType\":\"default\",\"namespace\":\"origin\",\"name\":\"Empty\"}");
    var location = new BehaviorDefinitionSource("origin.Empty", "workflow", "2.0");
    var first = new LegacyBusinessActionNormalizer.NormalizedBehavior(List.of(), List.of(), Map.of(), Map.of(),
        List.of(new BehaviorMemberSource("event", "declared", "definition", "generated", location)));
    var second = new LegacyBusinessActionNormalizer.NormalizedBehavior(List.of(), List.of(), Map.of(), Map.of(),
        List.of(new BehaviorMemberSource("event", "declared", "definition", "contribution", location)));
    var a = BusinessModelBehaviorResolver.resolve(meta.getFullName(), meta, List.of(), first, List.of(), List.of());
    var b = BusinessModelBehaviorResolver.resolve(meta.getFullName(), meta, List.of(), second, List.of(), List.of());
    assertNotEquals(a.getModelRevision(), b.getModelRevision());
    assertEquals(a.getModelRevision(), BusinessModelBehaviorResolver.resolve(meta.getFullName(), meta,
        List.of(), first, List.of(), List.of()).getModelRevision());
    assertEquals(a.getActions(), b.getActions());
  }

  private static void assertSource(BusinessModelBehavior behavior, String type, String name, String kind, String path) {
    var sources = behavior.getMemberSources().stream().filter(source -> type.equals(source.memberType())
        && name.equals(source.memberName()) && "definition".equals(source.role())).toList();
    assertEquals(1, sources.size(), type + "." + name);
    assertEquals(kind, sources.getFirst().kind());
    assertEquals(path, sources.getFirst().source().fieldPath());
  }

  private static DefaultBusinessModelMeta parse(String json) {
    return (DefaultBusinessModelMeta) BusinessModelMetaParser.parse(json).get();
  }

  private static BusinessModelProvider emptyProvider() {
    return new BusinessModelProvider() {
      public Optional<BusinessModel> getBusinessModel(String name) { return Optional.empty(); }
      public Map<String, BusinessModel> getBusinessModelMap() { return Map.of(); }
    };
  }
}
