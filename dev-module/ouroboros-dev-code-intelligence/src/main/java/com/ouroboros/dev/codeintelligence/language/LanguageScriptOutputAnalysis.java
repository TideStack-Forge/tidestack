package com.ouroboros.dev.codeintelligence.language;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.codeintelligence.type.TypeShape;

/** Output queries use the same environment pool as interactive documents, with independent bindings. */
public final class LanguageScriptOutputAnalysis implements ScriptOutputAnalysis {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Map<String, TypeShape> outputs = Collections.synchronizedMap(new LinkedHashMap<>() {
    protected boolean removeEldestEntry(Map.Entry<String, TypeShape> eldest) { return size() > 128; }
  });
  private final EditorLanguageServices services;
  private final CodeEnvironmentResolver environments;
  public LanguageScriptOutputAnalysis(EditorLanguageServices services, CodeEnvironmentResolver environments) {
    this.services = services; this.environments = environments;
  }
  public TypeShape infer(String app, ApplicationMetadataSnapshot application, ScriptAnalysisQuery query, CodeContextRequest request) {
    String language = "js".equals(query.language()) ? "javascript" : query.language();
    if (services.languages().stream().noneMatch(descriptor -> descriptor.languageId().equals(language) && descriptor.available()))
      return TypeShape.unknown("language-output-service-unavailable");
    final LanguageEnvironment environment;
    try { environment = environments.resolve(app, application.getRuntimeIdentity()).join(); }
    catch (java.util.concurrent.CompletionException failure) {
      org.slf4j.LoggerFactory.getLogger(LanguageScriptOutputAnalysis.class).warn("Script output environment is unavailable", failure);
      return TypeShape.unknown("language-output-environment-unavailable");
    }
    String key = language + ":" + environment.version() + ":" + query.key();
    var cached = outputs.get(key);
    if (cached != null) return cached;
    String session = UUID.randomUUID().toString();
    try {
      var open = message("textDocument/didOpen");
      open.withObject("params").putObject("textDocument").put("languageId", query.language()).put("version", request.documentVersion()).put("text", query.source());
      var context = message("ouroboros/context");
      var params = context.withObject("params").put("documentVersion", request.documentVersion());
      var globals = params.putObject("snapshot").putArray("globals");
      query.bindings().forEach((name, shape) -> globals.addObject().put("name", name).set("shape", JSON.valueToTree(shape)));
      var infer = message("ouroboros/inferOutput"); infer.put("id", UUID.randomUUID().toString());
      infer.withObject("params").put("documentVersion", request.documentVersion()).put("draftRevision", request.draftRevision()).putObject("textDocument");
      var responses = services.exchange(language, environment, session, List.of(open, context, infer)).join();
      var response = responses.stream().filter(value -> value.has("id")).findFirst().orElseThrow();
      if (response.has("error")) return TypeShape.unknown("language-output-error:" + response.get("error").path("message").asText());
      var result = response.path("result");
      if (result.path("documentVersion").asLong(-1) != request.documentVersion() || result.path("draftRevision").asLong(-1) != request.draftRevision())
        return TypeShape.unknown("stale-language-output");
      var shape = new TypeShape(result.path("shape").path("schema"));
      if (!shape.isUnknown()) outputs.put(key, shape);
      return shape;
    } catch (java.util.concurrent.CompletionException failure) {
      org.slf4j.LoggerFactory.getLogger(LanguageScriptOutputAnalysis.class).warn("Script output analysis failed for {}", query.language(), failure);
      return TypeShape.unknown("language-output-service-failed");
    } finally {
      try { services.closeSession(language, environment, session).join(); }
      catch (java.util.concurrent.CompletionException failure) { org.slf4j.LoggerFactory.getLogger(LanguageScriptOutputAnalysis.class).warn("Could not close output document", failure); }
    }
  }
  private ObjectNode message(String method) {
    var message = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method); message.putObject("params"); return message;
  }
}
