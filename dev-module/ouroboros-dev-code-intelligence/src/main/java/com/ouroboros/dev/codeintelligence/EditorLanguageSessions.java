package com.ouroboros.dev.codeintelligence;

import com.ouroboros.dev.codeintelligence.service.CodeContextService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.dev.codeintelligence.language.*;
import com.ouroboros.exception.BusinessException;
import jakarta.annotation.PreDestroy;

/** App-scoped, owner-bound LSP sessions on the platform language pool. */
public final class EditorLanguageSessions {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final CodeContextService contexts;
  private final EditorLanguageServices languages;
  private final CodeEnvironmentResolver environments;
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private boolean closed;
  private final java.util.concurrent.ScheduledExecutorService maintenance = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
    var thread = new Thread(r, "editor-language-sessions"); thread.setDaemon(true); return thread;
  });
  private record Session(String app, String owner, Map<String, Object> runtime, String language, String uri, LanguageEnvironment environment, java.util.concurrent.atomic.AtomicLong touched) {}
  public record OpenRequest(CodeContextRequest context, String text) {}
  public record OpenResult(String sessionId, String uri, String contextVersion, Object messages) {}

  public EditorLanguageSessions(CodeContextService contexts, EditorLanguageServices languages, CodeEnvironmentResolver environments) {
    this.contexts = contexts; this.languages = languages; this.environments = environments;
    maintenance.scheduleWithFixedDelay(this::expire, 1, 1, TimeUnit.MINUTES);
  }

  public CompletableFuture<OpenResult> open(String app, String owner, OpenRequest request) {
    if (request == null || request.context() == null || (request.context().language() == null || !request.context().language().matches("[a-z][a-z0-9-]{0,31}"))
        || request.text() == null || request.text().length() > 1_000_000) throw new BusinessException("缺少 代码文档或超出大小限制");
    var context = contexts.describe(app, request.context());
    requireRuntime(context);
    String id = UUID.randomUUID().toString(), uri = "ouroboros://language/" + id + "/script";
    return environments.resolve(app, context.runtimeIdentity()).thenCompose(environment -> {
      var session = new Session(app, owner, context.runtimeIdentity(), request.context().language(), uri, environment, new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis()));
      synchronized (sessions) {
        if (closed) throw new BusinessException("代码编辑服务已关闭");
        if (sessions.size() >= 256) throw new BusinessException("代码编辑会话已达容量上限");
        sessions.put(id, session);
      }
      ObjectNode initialize = message("initialize"); initialize.put("id", id + ":initialize"); initialize.withObject("params").putObject("capabilities");
      ObjectNode open = message("textDocument/didOpen");
      var document = open.withObject("params").putObject("textDocument");
      document.put("uri", uri).put("languageId", session.language()).put("version", request.context().documentVersion()).put("text", request.text());
      return send(id, session, List.of(initialize, open, contextMessage(session, context)))
          .thenApply(messages -> new OpenResult(id, uri, context.contextVersion(), messages))
          .whenComplete((result, error) -> { if (error != null) { sessions.remove(id); closeRemote(id, session).exceptionally(closeError -> { org.slf4j.LoggerFactory.getLogger(EditorLanguageSessions.class).warn("Could not close failed editor session", closeError); return null; }); } });
    }).whenComplete((result, error) -> {
      if (error != null) org.slf4j.LoggerFactory.getLogger(EditorLanguageSessions.class).warn("Could not open {} language session for {}", request.context().language(), app, error);
    });
  }

  public CompletableFuture<Object> messages(String app, String owner, String id, List<JsonNode> messages) {
    var session = session(app, owner, id);
    if (messages == null || messages.size() > 16 || JSON.valueToTree(messages).toString().length() > 2_000_000)
      throw new BusinessException("LSP 消息批次无效或过大");
    for (var message : messages) {
      String method = message.path("method").asText();
      if (!message.isObject() || !"2.0".equals(message.path("jsonrpc").asText()) || !message.path("params").isObject()
          || method.equals("initialize") || method.equals("ouroboros/context") || method.startsWith("workspace/")) throw new BusinessException("不允许的 LSP 消息");
      if (message.has("id")) ((ObjectNode) message).put("id", id + ":" + message.get("id").asText());
      if (method.equals("$/cancelRequest")) ((ObjectNode) message.get("params")).put("id", id + ":" + message.path("params").path("id").asText());
    }
    return send(id, session, messages);
  }

  public CompletableFuture<Object> context(String app, String owner, String id, CodeContextRequest request) {
    var session = session(app, owner, id);
    if (!session.language().equals(request.language())) throw new BusinessException("编辑会话的语言不可变更，请重新打开编辑器");
    var context = contexts.describe(app, request);
    requireRuntime(context);
    if (!context.runtimeIdentity().equals(session.runtime())) {
      sessions.remove(id); closeRemote(id, session).exceptionally(error -> { org.slf4j.LoggerFactory.getLogger(EditorLanguageSessions.class).warn("Could not close outdated editor session", error); return null; });
      throw new BusinessException("开发助手环境已变化，请重新打开代码编辑会话");
    }
    return send(id, session, List.of(contextMessage(session, context)));
  }

  public CompletableFuture<Object> close(String app, String owner, String id) {
    var session = session(app, owner, id);
    sessions.remove(id, session);
    return closeRemote(id, session);
  }

  private Session session(String app, String owner, String id) {
    var session = sessions.get(id);
    if (session == null || !session.app().equals(app) || !session.owner().equals(owner)) throw new BusinessException("代码编辑会话不存在或无权访问");
    session.touched().set(System.currentTimeMillis());
    return session;
  }

  private CompletableFuture<Object> send(String id, Session session, List<JsonNode> messages) {
    try { return languages.exchange(session.language(), session.environment(), id, messages).thenApply(result -> result); }
    catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
  }

  private CompletableFuture<Object> closeRemote(String id, Session session) {
    return languages.closeSession(session.language(), session.environment(), id).thenApply(ignored -> List.of());
  }

  private static void requireRuntime(CodeContextResponse context) {
    if (!"CURRENT".equals(context.runtimeStatus()) || !context.runtimeIdentity().containsKey("helperInstanceId"))
      throw new BusinessException("开发助手尚未就绪，代码语义服务需要应用的实际代码环境");
  }
  private static ObjectNode contextMessage(Session session, CodeContextResponse context) {
    ObjectNode message = message("ouroboros/context");
    message.withObject("params").put("uri", session.uri()).put("documentVersion", context.documentVersion()).set("snapshot", JSON.valueToTree(context.snapshot()));
    return message;
  }
  private static ObjectNode message(String method) {
    var message = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method);
    message.putObject("params"); return message;
  }
  private void expire() {
    long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
    sessions.forEach((id, session) -> {
      if (session.touched().get() < cutoff && sessions.remove(id, session)) closeRemote(id, session)
          .exceptionally(error -> { org.slf4j.LoggerFactory.getLogger(EditorLanguageSessions.class).warn("Could not expire editor session", error); return null; });
    });
  }
  @PreDestroy public void destroy() {
    synchronized (sessions) { closed = true; }
    maintenance.shutdownNow();
    sessions.forEach((id, session) -> closeRemote(id, session)
        .exceptionally(error -> { org.slf4j.LoggerFactory.getLogger(EditorLanguageSessions.class).warn("Could not close editor session", error); return null; }));
    synchronized (sessions) { closed = true; sessions.clear(); }
  }
}
