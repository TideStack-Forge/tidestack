package com.ouroboros.dev.codeintelligence;

import com.ouroboros.dev.codeintelligence.service.CodeContextService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.dev.codeintelligence.language.*;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.util.Json;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EditorLanguageSessionsTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final EditorLanguageServices languages = mock(EditorLanguageServices.class);
  private final CodeContextProvider provider = mock(CodeContextProvider.class);

  private CodeContextRequest request() {
    return Json.toBean(CodeContextRequest.class, """
        {"documentId":"inmemory://1","documentVersion":1,"draftRevision":1,"resource":{"type":"example"},
         "location":{"pointer":"/script"},"draft":{"script":""},"codeSite":"example.script","language":"groovy"}
        """);
  }
  private EditorLanguageSessions service(boolean online) {
    return service(online, (app, identity) -> CompletableFuture.completedFuture(new LanguageEnvironment("a".repeat(64), 21, List.of())));
  }
  private EditorLanguageSessions service(boolean online, CodeEnvironmentResolver environments) {
    when(provider.resourceTypes()).thenReturn(java.util.Set.of("example"));
    when(provider.describe(any(), any())).thenReturn(new CodeContextDescription("groovy", List.of(), null, List.of(), false, Map.of()));
    when(languages.exchange(anyString(), any(), anyString(), anyList()))
        .thenAnswer(invocation -> CompletableFuture.completedFuture(List.of()));
    var contexts = new CodeContextService(List.of(provider), List.of(app -> online
        ? ApplicationMetadataSnapshot.current(Map.of()).withRuntimeIdentity(Map.of("helperInstanceId", "helper-1")) : ApplicationMetadataSnapshot.unavailable()));
    when(languages.closeSession(anyString(), any(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
    return new EditorLanguageSessions(contexts, languages, environments);
  }
  @Test void opensOnPlatformAndKeepsOwnerAndAppBoundaries() throws Exception {
    var service = service(true);
    try {
      var opened = service.open("app", "owner", new EditorLanguageSessions.OpenRequest(request(), "def value=1")).join();
      assertTrue(opened.uri().contains(opened.sessionId()));
      assertThrows(RuntimeException.class, () -> service.messages("other", "owner", opened.sessionId(), List.of(JSON.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/hover\",\"params\":{}}"))));
      assertThrows(RuntimeException.class, () -> service.close("app", "another-owner", opened.sessionId()));
      var hover = JSON.readTree("{\"jsonrpc\":\"2.0\",\"id\":\"one\",\"method\":\"textDocument/hover\",\"params\":{}}");
      service.messages("app", "owner", opened.sessionId(), List.of(hover)).join();
      assertEquals(opened.sessionId() + ":one", hover.get("id").asText());
      service.close("app", "owner", opened.sessionId()).join();
      assertThrows(RuntimeException.class, () -> service.close("app", "owner", opened.sessionId()));
    } finally { service.destroy(); }
  }
  @Test void rejectsUnavailableRuntimeInvalidLanguageAndClientContextInjection() throws Exception {
    var offline = service(false);
    try { assertThrows(RuntimeException.class, () -> offline.open("app", "owner", new EditorLanguageSessions.OpenRequest(request(), ""))); }
    finally { offline.destroy(); }
    var service = service(true);
    try {
      var opened = service.open("app", "owner", new EditorLanguageSessions.OpenRequest(request(), "")).join();
      var injection = JSON.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"ouroboros/context\",\"params\":{}}");
      assertThrows(RuntimeException.class, () -> service.messages("app", "owner", opened.sessionId(), List.of(injection)));
      service.context("app", "owner", opened.sessionId(), request()).join();
      service.messages("app", "owner", opened.sessionId(), List.of()).join();
    } finally { service.destroy(); }
  }
  @Test void reportsAnalysisFailureAndRemovesUnopenedSession() {
    var service = service(true);
    when(languages.exchange(anyString(), any(), anyString(), anyList()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("platform engine unavailable")));
    try { assertTrue(assertThrows(RuntimeException.class, () -> service.open("app", "owner", new EditorLanguageSessions.OpenRequest(request(), "")).join()).getMessage().contains("platform engine unavailable")); }
    finally { service.destroy(); }
  }
  @Test void removesSessionWhenAProviderThrowsSynchronously() {
    var service = service(true);
    when(languages.exchange(anyString(), any(), anyString(), anyList())).thenThrow(new IllegalArgumentException("unknown language"));
    try {
      assertThrows(RuntimeException.class, () -> service.open("app", "owner", new EditorLanguageSessions.OpenRequest(request(), "")).join());
      verify(languages).closeSession(anyString(), any(), anyString());
    } finally { service.destroy(); }
  }
  @Test void rejectsLateEnvironmentResolutionAfterShutdown() {
    var pending = new CompletableFuture<LanguageEnvironment>();
    var service = service(true, (app, identity) -> pending);
    var opened = service.open("app", "owner", new EditorLanguageSessions.OpenRequest(request(), ""));
    service.destroy();
    pending.complete(new LanguageEnvironment("a".repeat(64), 21, List.of()));
    assertThrows(RuntimeException.class, opened::join);
    verify(languages, never()).exchange(anyString(), any(), anyString(), anyList());
  }
  @Test void enforcesCapacityAfterConcurrentEnvironmentResolutionAndReusesClosedSlots() {
    var pending = new CompletableFuture<LanguageEnvironment>();
    var service = service(true, (app, identity) -> pending);
    try {
      var opens = new java.util.ArrayList<CompletableFuture<EditorLanguageSessions.OpenResult>>();
      for (int i = 0; i < 257; i++) opens.add(service.open("app", "owner", new EditorLanguageSessions.OpenRequest(request(), "")));
      pending.complete(new LanguageEnvironment("a".repeat(64), 21, List.of()));
      assertEquals(256, opens.stream().filter(future -> !future.isCompletedExceptionally()).count());
      var first = opens.stream().filter(future -> !future.isCompletedExceptionally()).findFirst().orElseThrow().join();
      service.close("app", "owner", first.sessionId()).join();
      assertNotNull(service.open("app", "owner", new EditorLanguageSessions.OpenRequest(request(), "")).join());
    } finally { service.destroy(); }
  }
}
