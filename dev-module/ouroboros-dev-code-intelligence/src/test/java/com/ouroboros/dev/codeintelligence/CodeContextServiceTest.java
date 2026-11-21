package com.ouroboros.dev.codeintelligence;

import com.ouroboros.dev.codeintelligence.service.CodeContextService;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.metadata.*;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.script.intelligence.*;
import com.ouroboros.util.Json;

class CodeContextServiceTest {
  @Test void syntaxDiagnosticsUseTheSelectedFieldAndPreserveContextIdentity() {
    var service = new CodeContextService(List.of(provider(true)), List.of());
    var invalid = new CodeContextRequest("syntax-editor", 4, 8, "test.field", "spel",
        new CodeContextRequest.Resource("test", "model"), new CodeContextRequest.Location(null, "/expression"),
        Json.toBean(com.fasterxml.jackson.databind.JsonNode.class, "{\"expression\":\"${value +}\"}"));
    var result = service.describe("offline-app", invalid);
    assertEquals("syntax-editor", result.documentId()); assertEquals(4, result.documentVersion());
    assertEquals("UNAVAILABLE", result.runtimeStatus());
    assertEquals(true, result.snapshot().getLanguageProjectionHints().get("syntaxDiagnostics"));
    assertEquals(1, result.snapshot().getDiagnostics().size());
    assertEquals("ouroboros-syntax", result.snapshot().getDiagnostics().getFirst().get("source"));
    var corrected = new CodeContextRequest(invalid.documentId(), 5, 9, invalid.codeSite(), invalid.language(), invalid.resource(), invalid.location(),
        Json.toBean(com.fasterxml.jackson.databind.JsonNode.class, "{\"expression\":\"${value + 1}\"}"));
    var valid = service.describe("offline-app", corrected);
    assertTrue(valid.snapshot().getDiagnostics().isEmpty());
    assertNotEquals(result.contextVersion(), valid.contextVersion());
  }
  @Test void referenceCandidatesDoNotChangeApplicationAvailabilityOrDraftOrigins() {
    var reference = new ScriptIntelligenceSnapshot();
    var tool = new ScriptIntelligenceGlobal(); tool.setName("PlatformTool");
    reference.setGlobals(List.of(tool));
    var javaType = new ScriptIntelligenceJavaType(); javaType.setSimpleName("PlatformType");
    reference.setJavaTypes(List.of(javaType));
    var service = new CodeContextService(List.of(provider(true)), List.of(), language -> reference);
    var response = service.describe("offline-app", request(1));
    assertEquals("UNAVAILABLE", response.runtimeStatus());
    assertEquals("dev-platform-fallback", response.snapshot().getSource());
    assertEquals(CodeCandidateOrigin.DRAFT_INFERENCE, response.snapshot().getGlobals().get(0).getOrigin());
    assertEquals(CodeCandidateOrigin.PLATFORM_REFERENCE, response.snapshot().getGlobals().get(1).getOrigin());
    assertEquals(CodeCandidateOrigin.PLATFORM_REFERENCE, response.snapshot().getJavaTypes().get(0).getOrigin());
    assertEquals("PLATFORM_REFERENCE", Json.toMap(Json.toJsonString(tool)).get("origin"));
  }

  @Test void applicationCatalogTakesPrecedenceOverReferenceCandidates() {
    var runtime = new ScriptIntelligenceSnapshot();
    var tool = new ScriptIntelligenceGlobal(); tool.setName("AppTool"); runtime.setGlobals(List.of(tool));
    var item = new Metadata(); item.setName("spel"); item.setDetails(Json.toMap(Json.toJsonString(runtime)));
    var service = new CodeContextService(List.of(provider(true)), List.of(app -> ApplicationMetadataSnapshot.current(
        Map.of("script-intelligence", List.of(item)))), language -> { throw new AssertionError("reference environment used"); });
    var response = service.describe("app", request(1));
    assertEquals(CodeCandidateOrigin.APPLICATION_SNAPSHOT, response.snapshot().getGlobals().get(1).getOrigin());
    assertEquals("CURRENT", response.runtimeStatus());
  }

  private static CodeContextRequest request(long version) {
    return new CodeContextRequest("editor-a", version, 8, "test.field", "spel", new CodeContextRequest.Resource("test", "model"),
        new CodeContextRequest.Location(null, "/expression"), Json.toBean(com.fasterxml.jackson.databind.JsonNode.class, "{}"));
  }

  private static CodeContextProvider provider(boolean allowGlobals) {
    return new CodeContextProvider() {
      public Set<String> resourceTypes() { return Set.of("test"); }
      public CodeContextDescription describe(CodeContextRequest request, ReadOnlyMetadataView view) {
        var local = new ScriptIntelligenceGlobal(); local.setName("local"); local.setShape(TypeShape.scalar("string"));
        return new CodeContextDescription("spel", List.of(local), TypeShape.scalar("boolean"), List.of(), allowGlobals);
      }
    };
  }

  @Test void preservesDocumentIdentityAndUsesOnlyTheRequestedApplication() {
    var calls = new java.util.ArrayList<String>();
    var runtime = new ScriptIntelligenceSnapshot();
    var global = new ScriptIntelligenceGlobal(); global.setName("Config"); global.setShape(TypeShape.object(Map.of(), "java"));
    runtime.setGlobals(List.of(global));
    var item = new Metadata(); item.setName("spel"); item.setDetails(Json.toMap(Json.toJsonString(runtime)));
    ApplicationMetadataSnapshotProvider source = app -> {
      calls.add(app);
      return ApplicationMetadataSnapshot.stale(Map.of("script-intelligence", List.of(item)))
          .withRuntimeIdentity(Map.of("refreshRevision", 42, "appIncarnationId", app));
    };
    var service = new CodeContextService(List.of(provider(true)), List.of(source));
    var first = service.describe("application-a", request(3));
    var second = service.describe("application-a", request(4));
    assertEquals("editor-a", first.documentId()); assertEquals(3, first.documentVersion()); assertEquals(8, first.draftRevision());
    assertEquals("STALE", first.runtimeStatus()); assertEquals(42, first.runtimeIdentity().get("refreshRevision"));
    assertEquals(List.of("local", "Config"), first.snapshot().getGlobals().stream().map(ScriptIntelligenceGlobal::getName).toList());
    assertEquals(first.contextVersion(), second.contextVersion());
    assertNotEquals(first.contextVersion(), service.describe("application-b", request(3)).contextVersion());
    assertEquals(List.of("application-a", "application-a", "application-b"), calls);
  }

  @Test void restrictedProfilesDoNotReceiveGlobalObjectsAndUnavailableIsExplicit() {
    var response = new CodeContextService(List.of(provider(false)), List.of()).describe("app", request(0));
    assertEquals("UNAVAILABLE", response.runtimeStatus());
    assertEquals("unavailable", response.snapshot().getSource());
    assertEquals(List.of("local"), response.snapshot().getGlobals().stream().map(ScriptIntelligenceGlobal::getName).toList());
    assertTrue(response.snapshot().getJavaTypes().isEmpty());
  }

  @Test void rejectsAmbiguousSourcesProvidersAndInvalidRequests() {
    assertThrows(IllegalArgumentException.class, () -> new CodeContextService(List.of(provider(true), provider(false)), List.of()));
    ApplicationMetadataSnapshotProvider source = app -> ApplicationMetadataSnapshot.current(Map.of());
    var service = new CodeContextService(List.of(provider(true)), List.of(source, source));
    assertThrows(BusinessException.class, () -> service.describe("app", request(0)));
    assertThrows(BusinessException.class, () -> service.describe("app", request(-1)));
    assertThrows(BusinessException.class, () -> service.describe("app", null));
    assertThrows(BusinessException.class, () -> new CodeContextService(List.of(), List.of()).describe("app", request(0)));
  }

  @Test void environmentAndDomainProjectionHintsAreMergedAndRuntimeIdentityIsImmutable() {
    var snapshot = ApplicationMetadataSnapshot.current(Map.of());
    var identity = new java.util.LinkedHashMap<String, Object>(); identity.put("refreshRevision", 1);
    var next = snapshot.withRuntimeIdentity(identity); identity.put("refreshRevision", 2);
    assertTrue(snapshot.getRuntimeIdentity().isEmpty()); assertEquals(1, next.getRuntimeIdentity().get("refreshRevision"));
    var environment = new ScriptIntelligenceSnapshot(); environment.setLanguageProjectionHints(Map.of("host", "graaljs"));
    var item = new Metadata(); item.setName("spel"); item.setDetails(Json.toMap(Json.toJsonString(environment)));
    var result = new CodeContextService(List.of(provider(true)), List.of(app -> ApplicationMetadataSnapshot.current(Map.of("script-intelligence", List.of(item)))))
        .describe("app", request(1));
    assertEquals("graaljs", result.snapshot().getLanguageProjectionHints().get("host"));
  }

  @Test void rejectsInvalidEnvironmentCatalogInsteadOfQuietlyUsingAnotherEnvironment() {
    var item = new Metadata(); item.setName("spel");
    ApplicationMetadataSnapshotProvider duplicate = app -> ApplicationMetadataSnapshot.current(Map.of("script-intelligence", List.of(item, item)));
    assertThrows(BusinessException.class, () -> new CodeContextService(List.of(provider(true)), List.of(duplicate)).describe("app", request(0)));
    ApplicationMetadataSnapshotProvider missing = app -> ApplicationMetadataSnapshot.current(Map.of("script-intelligence", List.of(item)));
    assertThrows(BusinessException.class, () -> new CodeContextService(List.of(provider(true)), List.of(missing)).describe("app", request(0)));
  }
}
