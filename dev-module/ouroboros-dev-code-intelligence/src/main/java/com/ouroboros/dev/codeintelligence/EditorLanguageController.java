package com.ouroboros.dev.codeintelligence;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.web.WebApiResult;
import io.vavr.control.Try;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/api/dev/{appName}/code-intelligence/sessions")
public final class EditorLanguageController {
  private final EditorLanguageSessions sessions;
  private final long openTimeoutMillis;
  public EditorLanguageController(EditorLanguageSessions sessions,
      @Value("${ouroboros.code-intelligence.language.open-timeout-millis:120000}") long openTimeoutMillis) {
    this.sessions = sessions; this.openTimeoutMillis = openTimeoutMillis;
  }
  @PostMapping
  public DeferredResult<WebApiResult<EditorLanguageSessions.OpenResult>> open(@PathVariable String appName,
      @RequestBody EditorLanguageSessions.OpenRequest request, HttpServletRequest http) {
    var sessionOwner = owner(http);
    var operation = sessions.open(appName, sessionOwner, request);
    var response = new DeferredResult<WebApiResult<EditorLanguageSessions.OpenResult>>(openTimeoutMillis);
    response.onTimeout(() -> response.setErrorResult(WebApiResult.of(Try.failure(
        new com.ouroboros.exception.BusinessException("应用分析环境准备超时，请稍后重新打开编辑器")))));
    operation.whenComplete((result, error) -> {
      if (!response.setResult(WebApiResult.of(error == null ? Try.success(result) : Try.failure(error))) && result != null)
        sessions.close(appName, sessionOwner, result.sessionId()).exceptionally(failure -> {
          org.slf4j.LoggerFactory.getLogger(EditorLanguageController.class).warn("Could not close abandoned editor session", failure); return null;
        });
    });
    return response;
  }
  @GetMapping("/{sessionId}/notifications")
  public CompletableFuture<WebApiResult<Object>> notifications(@PathVariable String appName, @PathVariable String sessionId, HttpServletRequest http) {
    return wrap(sessions.messages(appName, owner(http), sessionId, List.of()));
  }
  @PostMapping("/{sessionId}/messages")
  public CompletableFuture<WebApiResult<Object>> messages(@PathVariable String appName, @PathVariable String sessionId,
      @RequestBody List<JsonNode> messages, HttpServletRequest http) {
    return wrap(sessions.messages(appName, owner(http), sessionId, messages));
  }
  @PostMapping("/{sessionId}/context")
  public CompletableFuture<WebApiResult<Object>> context(@PathVariable String appName, @PathVariable String sessionId,
      @RequestBody CodeContextRequest request, HttpServletRequest http) {
    return wrap(sessions.context(appName, owner(http), sessionId, request));
  }
  @DeleteMapping("/{sessionId}")
  public CompletableFuture<WebApiResult<Object>> close(@PathVariable String appName, @PathVariable String sessionId, HttpServletRequest http) {
    return wrap(sessions.close(appName, owner(http), sessionId));
  }
  private static String owner(HttpServletRequest request) { return request.getSession().getId(); }
  private static CompletableFuture<WebApiResult<Object>> wrap(CompletableFuture<Object> operation) {
    // AMIS normalizes a top-level data array to data.items; keep LSP batches in a named field.
    return operation.handle((result, error) -> WebApiResult.of(error == null ? Try.success((Object) java.util.Map.of("messages", result)) : Try.failure(error)));
  }
}
