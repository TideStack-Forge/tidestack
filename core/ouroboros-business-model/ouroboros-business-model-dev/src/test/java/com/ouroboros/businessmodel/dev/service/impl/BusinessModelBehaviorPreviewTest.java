package com.ouroboros.businessmodel.dev.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModelMetaParser;
import com.ouroboros.businessmodel.dev.vo.BusinessModelValidationState;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.*;

class BusinessModelBehaviorPreviewTest {
  @Test
  void fullDraftOverridesStoredDefinitionAndDoesNotWritePublishOrRequireARunningApplication() {
    var service = new ReadOnlyService();
    String draft = """
        {"modelType":"default","formatVersion":"2.0","namespace":"preview","name":"Order","title":"订单",
         "events":[{"name":"draftEvent","subject":"model","occurrenceCondition":"业务确认后由实现显式发出","allowedProducerKinds":["action"],"payloadSchema":{"type":"object"}}]}
        """;
    var first = service.previewBehavior(draft, "request-1");
    assertEquals("draft", first.view());
    assertEquals("request-1", first.requestId());
    assertEquals("development-environment-only", first.codeContributionCoverage());
    assertNotNull(first.catalog());
    assertEquals(BusinessModelValidationState.VALID, first.validationSnapshot().getValidationState(), first.validationSnapshot().getValidationErrors().toString());
    assertEquals(List.of("draftEvent"), first.catalog().events().stream().map(event -> event.getName()).toList());
    assertEquals("metadata", first.catalog().sources().getFirst().kind());
    var second = service.previewBehavior(draft.replace("draftEvent", "newEvent"), "request-2");
    assertNotEquals(first.draftFingerprint(), second.draftFingerprint());
    assertNotEquals(first.catalog().revision(), second.catalog().revision());
    assertEquals("request-2", second.requestId());
    assertEquals(first.draftFingerprint(), service.previewBehavior(draft, "request-3").draftFingerprint());
    assertFalse(Json.toJsonString(service.getOne("preview.Order").orElseThrow()).contains("draftEvent"));
  }

  @Test
  void incompleteJsonAndInvalidVersionsReturnDiagnosticsWithoutAFalseEmptyCatalog() {
    var service = new ReadOnlyService();
    for (String draft : List.of("{", "{\"modelType\":\"default\",\"formatVersion\":null}",
        "{\"modelType\":\"default\",\"name\":\"Order\",\"formatVersion\":\"3.0\"}")) {
      var preview = service.previewBehavior(draft, "invalid-draft");
      assertNull(preview.catalog());
      assertEquals("invalid-draft", preview.requestId());
      assertEquals(BusinessModelValidationState.INVALID, preview.validationSnapshot().getValidationState());
      assertFalse(preview.validationSnapshot().getValidationDiagnostics().isEmpty());
    }
  }

  @Test
  void conflictsRemainVisibleInDraftCatalogAlongsideOfficialValidationDiagnostics() {
    var preview = new ReadOnlyService().previewBehavior("""
        {"modelType":"default","namespace":"preview","name":"Order","title":"订单",
         "events":[{"name":"same","subject":"model"},{"name":"same","subject":"model"}]}
        """, "duplicate");
    assertNotNull(preview.catalog());
    assertEquals(2, preview.catalog().events().size());
    assertEquals(2, preview.catalog().sources().size());
    assertEquals(BusinessModelValidationState.INVALID, preview.validationSnapshot().getValidationState());
    assertTrue(preview.validationSnapshot().getValidationErrors().toString().contains("duplicate"));
  }

  @Test
  void derivedViewsAreDiscardedAtTheExistingPermanentMetadataBoundary() {
    var service = new ReadOnlyService();
    var model = service.buildModelFromRequestBody("""
        {"modelType":"default","name":"Order","behaviorPreview":{"requestId":"stale"},
         "behaviorCatalog":{"actions":[]},"behaviorFormatUpgrade":{"planFingerprint":"stale"},
         "unknownExtension":{"keep":true}}
        """).get();
    var saved = Json.toMap(service.permanent(model));
    assertFalse(saved.containsKey("behaviorPreview"));
    assertFalse(saved.containsKey("behaviorCatalog"));
    assertFalse(saved.containsKey("behaviorFormatUpgrade"));
    assertEquals(Map.of("keep", true), saved.get("unknownExtension"));
    assertFalse(saved.containsKey("formatVersion"));
  }

  private static final class ReadOnlyService extends BusinessModelDevService {
    private final BusinessModelMeta stored = BusinessModelMetaParser.parse("""
        {"modelType":"default","namespace":"preview","name":"Order","title":"订单",
         "events":[{"name":"storedEvent","subject":"model"}]}
        """).get();

    public Optional<BusinessModelMeta> getOne(String name) { return Optional.of(stored); }
    public List<BusinessModelMeta> getAll() { return List.of(stored); }
    public io.vavr.control.Try<BusinessModelMeta> create(BusinessModelMeta model, String message) {
      throw new AssertionError("Preview must not create a model");
    }
    public io.vavr.control.Try<BusinessModelMeta> update(String name, BusinessModelMeta model) {
      throw new AssertionError("Preview must not save a model");
    }
    public Optional<com.ouroboros.businessmodel.projection.ModelBundle> getModelBundle(String name) {
      throw new AssertionError("Preview must not publish or project data structures");
    }
    String permanent(BusinessModelMeta model) { return getPermanentJson(model).get(); }
  }
}
