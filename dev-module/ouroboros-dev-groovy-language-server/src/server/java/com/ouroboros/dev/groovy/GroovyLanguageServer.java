package com.ouroboros.dev.groovy;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import com.google.gson.JsonObject;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Variable;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.*;

/** LSP transport and lifecycle. All JDT state is confined to one model executor. */
public final class GroovyLanguageServer implements LanguageServer, LanguageClientAware, TextDocumentService {
  private final ExecutorService model = Executors.newSingleThreadExecutor(r -> new Thread(r, "groovy-model"));
  private final GroovyWorkspace workspace;
  private final CompletableFuture<Void> exited = new CompletableFuture<>();
  private LanguageClient client;
  private boolean initialized;
  private boolean shutdown;

  private GroovyLanguageServer(String classpath) throws Exception { workspace = new GroovyWorkspace(classpath); }

  public static void run(InputStream input, OutputStream output, String classpath) throws Exception {
    var server = new GroovyLanguageServer(classpath);
    var transport = Executors.newCachedThreadPool();
    var launcher = LSPLauncher.createServerLauncher(server, input, output, transport, consumer -> consumer);
    server.connect(launcher.getRemoteProxy());
    var listening = launcher.startListening();
    try {
      CompletableFuture.runAsync(() -> {
        try { listening.get(); server.exited.complete(null); }
        catch (Exception e) { server.exited.completeExceptionally(e); }
      }, transport);
      server.exited.get();
    } finally {
      listening.cancel(true);
      server.model.submit(() -> { try { server.workspace.close(); } catch (Exception e) { throw new IllegalStateException(e); } }).get();
      server.model.shutdownNow();
      transport.shutdownNow();
    }
  }

  @Override public void connect(LanguageClient client) { this.client = client; }
  @Override public TextDocumentService getTextDocumentService() { return this; }
  @Override public WorkspaceService getWorkspaceService() { return new WorkspaceService() {
    @Override public void didChangeConfiguration(DidChangeConfigurationParams params) { }
    @Override public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) { }
  }; }

  @Override public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    return request(() -> {
      if (initialized) throw new IllegalStateException("Server is already initialized");
      initialized = true;
      var capabilities = new ServerCapabilities();
      capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);
      capabilities.setCompletionProvider(new CompletionOptions(false, List.of(".", "$")));
      capabilities.setHoverProvider(true);
      capabilities.setSignatureHelpProvider(new SignatureHelpOptions(List.of("(", ",")));
      capabilities.setDefinitionProvider(true);
      capabilities.setReferencesProvider(true);
      capabilities.setRenameProvider(true);
      capabilities.setDocumentSymbolProvider(true);
      capabilities.setDocumentFormattingProvider(true);
      capabilities.setCodeActionProvider(new CodeActionOptions(List.of(CodeActionKind.QuickFix)));
      capabilities.setSemanticTokensProvider(new SemanticTokensWithRegistrationOptions(new SemanticTokensLegend(List.of("variable", "method", "property", "type", "parameter"), List.of()), true));
      capabilities.setExperimental(Map.of("ouroboros", Map.of("protocolVersion", 1, "inferOutput", true)));
      return new InitializeResult(capabilities, new ServerInfo("Ouroboros Groovy", "4.0.30"));
    });
  }

  @Override public CompletableFuture<Object> shutdown() { return request(() -> { shutdown = true; return null; }); }
  @Override public void exit() { exited.complete(null); }
  @Override public void didSave(DidSaveTextDocumentParams params) { }

  @Override public void didOpen(DidOpenTextDocumentParams params) {
    notification(() -> { ready(); workspace.open(params.getTextDocument()); publish(params.getTextDocument().getUri()); });
  }
  @Override public void didChange(DidChangeTextDocumentParams params) {
    notification(() -> { ready(); workspace.change(params); publish(params.getTextDocument().getUri()); });
  }
  @Override public void didClose(DidCloseTextDocumentParams params) {
    notification(() -> { workspace.close(params.getTextDocument().getUri()); client.publishDiagnostics(new PublishDiagnosticsParams(params.getTextDocument().getUri(), List.of())); });
  }

  public static final class ContextUpdate {
    public String uri;
    public int documentVersion;
    public JsonObject snapshot;
  }

  @JsonNotification("ouroboros/context")
  public void context(ContextUpdate update) {
    notification(() -> {
      var document = workspace.document(update.uri);
      if (update.documentVersion != document.version) return;
      var response = new JsonObject();
      var snapshot = new JsonObject();
      snapshot.add("globals", update.snapshot.get("globals"));
      response.add("snapshot", snapshot);
      document.context(response.toString());
      publish(update.uri);
    });
  }

  public static final class OutputQuery {
    public TextDocumentIdentifier textDocument;
    public int documentVersion;
    public long draftRevision;
  }
  @JsonRequest("ouroboros/inferOutput")
  public CompletableFuture<JsonObject> inferOutput(OutputQuery query) {
    return request(() -> {
      ready();
      var document = workspace.document(query.textDocument.getUri());
      if (query.documentVersion != document.version) throw new IllegalArgumentException("Output document version is stale");
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      var snapshot = com.google.gson.JsonParser.parseString(document.context).getAsJsonObject().getAsJsonObject("snapshot");
      var bindings = new java.util.LinkedHashMap<String, com.ouroboros.codeintelligence.type.TypeShape>();
      if (snapshot != null && snapshot.has("globals")) for (var binding : snapshot.getAsJsonArray("globals")) {
        var item = binding.getAsJsonObject();
        if (item.has("shape")) bindings.put(item.get("name").getAsString(), new com.ouroboros.codeintelligence.type.TypeShape(mapper.readTree(item.getAsJsonObject("shape").get("schema").toString())));
      }
      var output = new GroovyOutputInference(document).inferOutput(bindings).annotated("x-evidence", "groovy-typed-module");
      var response = new JsonObject(); response.addProperty("documentVersion", document.version); response.addProperty("draftRevision", query.draftRevision);
      response.add("shape", com.google.gson.JsonParser.parseString(mapper.writeValueAsString(output)));
      return response;
    });
  }

  /** Orders the existing helper request/response transport after preceding LSP notifications. */
  @JsonRequest("ouroboros/barrier")
  public CompletableFuture<JsonObject> barrier() {
    return request(() -> {
      var result = new JsonObject();
      result.addProperty("groovyVersion", groovy.lang.GroovySystem.getVersion());
      result.addProperty("heapUsedBytes", java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
      return result;
    });
  }

  @Override public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
    return request(() -> { ready(); return Either.forLeft(GroovyCompletions.complete(workspace.document(params.getTextDocument().getUri()), params.getPosition())); });
  }
  @Override public CompletableFuture<Hover> hover(HoverParams params) { return request(() -> workspace.hover(params)); }
  @Override public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) { return request(() -> workspace.signature(params)); }
  @Override public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
    return request(() -> Either.forLeft(workspace.definition(params)));
  }
  @Override public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    return request(() -> workspace.references(params, params.getContext().isIncludeDeclaration()));
  }
  @Override public CompletableFuture<WorkspaceEdit> rename(RenameParams params) { return request(() -> workspace.rename(params)); }
  @Override public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
    return request(() -> workspace.symbols(params));
  }
  @Override public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
    return request(() -> GroovySyntax.indentation(workspace.document(params.getTextDocument().getUri()), params.getOptions()));
  }
  @Override public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
    return request(() -> {
      var document = workspace.document(params.getTextDocument().getUri());
      String recovered = GroovySyntax.closeDelimiters(document.source);
      if (recovered.equals(document.source) || document.diagnostics().isEmpty()) return List.of();
      var action = new CodeAction("补齐缺失的结束括号");
      action.setKind(CodeActionKind.QuickFix);
      action.setDiagnostics(document.diagnostics());
      action.setEdit(new WorkspaceEdit(List.of(Either.forLeft(new TextDocumentEdit(
          new VersionedTextDocumentIdentifier(document.uri, document.version),
          List.of(new TextEdit(document.range(document.source.length(), document.source.length()), recovered.substring(document.source.length()))))))));
      return List.of(Either.forRight(action));
    });
  }
  @Override public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
    return request(() -> {
      var document = workspace.document(params.getTextDocument().getUri());
      var tokens = new java.util.TreeMap<Integer, GroovyDocument.Occurrence>();
      for (var occurrence : document.analyze()) {
        int start = occurrence.node().getStart(), end = occurrence.node().getEnd();
        if (start < 0 || end > document.source.length() || end <= start) continue;
        String text = document.source.substring(start, end);
        if (Character.isJavaIdentifierStart(text.charAt(0)) && text.chars().allMatch(Character::isJavaIdentifierPart)) {
          tokens.merge(start, occurrence, (first, second) -> first.node().getLength() < second.node().getLength() ? first : second);
        }
      }
      var data = new ArrayList<Integer>();
      int previousLine = 0, previousColumn = 0;
      for (var occurrence : tokens.values()) {
        var position = document.position(occurrence.node().getStart());
        int type = occurrence.declaration() instanceof MethodNode ? 1 : occurrence.declaration() instanceof org.codehaus.groovy.ast.ClassNode ? 3
            : occurrence.declaration() instanceof org.codehaus.groovy.ast.Parameter ? 4 : occurrence.declaration() instanceof Variable ? 0 : 2;
        data.add(position.getLine() - previousLine);
        data.add(position.getLine() == previousLine ? position.getCharacter() - previousColumn : position.getCharacter());
        data.add(occurrence.node().getLength()); data.add(type); data.add(0);
        previousLine = position.getLine(); previousColumn = position.getCharacter();
      }
      return new SemanticTokens(Integer.toString(document.version), data);
    });
  }

  private void ready() { if (!initialized || shutdown) throw new IllegalStateException("Language server is not ready"); }
  private void publish(String uri) {
    var document = workspace.document(uri);
    client.publishDiagnostics(new PublishDiagnosticsParams(uri, document.diagnostics(), document.version));
  }
  private interface Operation<T> { T run() throws Exception; }
  private interface Notification { void run() throws Exception; }
  private <T> CompletableFuture<T> request(Operation<T> operation) {
    return CompletableFutures.computeAsync(model, cancel -> {
      cancel.checkCanceled();
      try {
        T result = operation.run();
        cancel.checkCanceled();
        return result;
      } catch (RuntimeException e) { throw e; }
      catch (Exception e) { throw new IllegalStateException("Groovy language operation failed", e); }
    });
  }
  private void notification(Notification operation) {
    model.execute(() -> {
      try { operation.run(); }
      catch (Exception e) {
        e.printStackTrace(System.err);
        client.logMessage(new MessageParams(MessageType.Error, "Groovy language notification failed: " + e));
      }
    });
  }
}
