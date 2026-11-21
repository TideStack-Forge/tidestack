package com.ouroboros.dev.codeintelligence.language;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Owns one stdio LSP process. It never loads compiler or application classes into the platform JVM. */
public final class LanguageServerConnection implements AutoCloseable {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_FRAME_BYTES = 8_000_000;
  private final InputStream input;
  private final java.io.OutputStream output;
  private final Runnable cleanup;
  private Runnable leaseCleanup = () -> {};
  private final java.util.function.BooleanSupplier alive;
  private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
  private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
  private final Consumer<JsonNode> notifications;

  private LanguageServerConnection(InputStream input, java.io.OutputStream output, Runnable cleanup,
      java.util.function.BooleanSupplier alive, Consumer<JsonNode> notifications) {
    this.input = input; this.output = output; this.cleanup = cleanup; this.alive = alive; this.notifications = notifications;
    Thread.startVirtualThread(this::readMessages);
  }

  public static LanguageServerConnection stdio(java.util.List<String> command, Path workspace, Consumer<JsonNode> notifications) throws IOException {
    var process = new ProcessBuilder(command).directory(workspace.toFile()).redirectError(workspace.resolve("language-server.log").toFile()).start();
    return new LanguageServerConnection(process.getInputStream(), process.getOutputStream(), () -> {
      process.destroy();
      try { if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly(); }
      catch (InterruptedException e) { process.destroyForcibly(); Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while closing language process", e); }
    }, process::isAlive, notifications);
  }

  public static LanguageServerConnection tcp(java.net.InetSocketAddress address, Consumer<JsonNode> notifications) throws IOException {
    var socket = new java.net.Socket();
    try {
      socket.connect(address, 10_000); socket.setTcpNoDelay(true);
      return new LanguageServerConnection(socket.getInputStream(), socket.getOutputStream(), () -> {
        try { socket.close(); } catch (IOException e) { throw new IllegalStateException("Could not close language connection", e); }
      }, () -> !socket.isClosed(), notifications);
    } catch (IOException | RuntimeException error) {
      try { socket.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
      throw error;
    }
  }

  public boolean isAlive() { return !closed.get() && alive.getAsBoolean(); }

  public CompletableFuture<JsonNode> send(JsonNode message) {
    CompletableFuture<JsonNode> response = new CompletableFuture<>();
    String id = message.has("id") && message.has("method") ? message.get("id").toString() : null;
    if (id != null && pending.putIfAbsent(id, response) != null) return CompletableFuture.failedFuture(new IllegalArgumentException("Duplicate LSP request id"));
    try {
      byte[] body = JSON.writeValueAsBytes(message);
      if (body.length > MAX_FRAME_BYTES) throw new IllegalArgumentException("LSP frame is too large");
      synchronized (output) {
        output.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
      }
      if (id == null) response.complete(null);
    } catch (IOException | RuntimeException e) {
      if (id != null) pending.remove(id, response);
      response.completeExceptionally(e);
    }
    if (id != null) response.orTimeout(30, TimeUnit.SECONDS).whenComplete((result, failure) -> pending.remove(id, response));
    return response;
  }

  private void readMessages() {
    try (var input = new BufferedInputStream(this.input)) {
      while (true) {
        String line = readLine(input);
        if (line == null) throw new IOException("Language connection closed by peer");
        int length = -1;
        while (!line.isEmpty()) {
          int colon = line.indexOf(':');
          if (colon < 0) throw new IOException("Invalid LSP header");
          if (line.substring(0, colon).equalsIgnoreCase("Content-Length")) length = Integer.parseInt(line.substring(colon + 1).trim());
          line = readLine(input);
          if (line == null) throw new IOException("Truncated LSP header");
        }
        if (length < 0 || length > MAX_FRAME_BYTES) throw new IOException("Invalid LSP content length");
        byte[] body = input.readNBytes(length);
        if (body.length != length) throw new IOException("Truncated LSP frame");
        JsonNode message = JSON.readTree(body);
        if (message.has("id") && !message.has("method")) {
          var response = pending.remove(message.get("id").toString());
          if (response != null) response.complete(message);
        } else if (message.has("id")) {
          // Dynamic registration and server-initiated commands are deliberately not advertised.
          var reply = JSON.createObjectNode().put("jsonrpc", "2.0").set("id", message.get("id"));
          ((ObjectNode) reply).putObject("error").put("code", -32601).put("message", "Unsupported client request");
          send(reply).exceptionally(error -> { org.slf4j.LoggerFactory.getLogger(LanguageServerConnection.class).warn("Could not answer server request", error); return null; });
        } else notifications.accept(message);
      }
    } catch (IOException | RuntimeException e) {
      failPending(e);
      close();
    }
  }

  private static String readLine(InputStream input) throws IOException {
    var line = new StringBuilder();
    for (int c; (c = input.read()) >= 0;) {
      if (c == '\n') return line.toString();
      if (c != '\r') line.append((char) c);
      if (line.length() > 8192) throw new IOException("LSP header exceeds limit");
    }
    return line.isEmpty() ? null : line.toString();
  }

  private void failPending(Throwable cause) {
    pending.forEach((id, response) -> response.completeExceptionally(cause));
    pending.clear();
  }

  public synchronized void onClose(Runnable cleanup) {
    if (closed.get()) cleanup.run(); else leaseCleanup = cleanup;
  }

  @Override public synchronized void close() {
    if (!closed.compareAndSet(false, true)) return;
    failPending(new IOException("Language connection closed"));
    try { cleanup.run(); } finally { leaseCleanup.run(); }
  }
}
