package com.ouroboros.dev.language;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Generic dedicated-container host. Engine commands are image-owned, never supplied by clients. */
public final class LanguageServiceHost {
  private static final Gson JSON = new Gson();
  private final Path storage;
  private final JsonObject engine;
  private final Map<String, Long> artifacts = new LinkedHashMap<>();
  private JsonObject manifest;

  private LanguageServiceHost(Path storage, JsonObject engine) { this.storage = storage; this.engine = engine; }

  public static void main(String[] args) throws Exception {
    Path storage = Path.of(args[1]); Files.createDirectories(storage);
    var engine = JsonParser.parseString(Files.readString(Path.of(args[2]))).getAsJsonObject();
    boolean leased = Boolean.parseBoolean(System.getenv("OUROBOROS_LANGUAGE_LEASED"));
    try (var listener = new ServerSocket(Integer.parseInt(args[0]))) {
      if (leased) listener.setSoTimeout(60000);
      Files.writeString(storage.resolve("ready"), "ready");
      // One host is leased to one immutable engine/environment. Editors multiplex over its LSP process.
      while (!Thread.currentThread().isInterrupted()) try (var socket = listener.accept()) {
        socket.setTcpNoDelay(true); socket.setSoTimeout(900000);
        new LanguageServiceHost(storage, engine).serve(socket);
        if (leased) return;
      } catch (IOException | RuntimeException error) {
        System.err.println("Language host connection failed: " + error);
        if (leased) return;
      }
    }
  }

  private void serve(Socket socket) throws Exception {
    var input = new BufferedInputStream(socket.getInputStream());
    var output = socket.getOutputStream();
    while (true) {
      JsonObject request = read(input);
      if (request == null) return;
      var response = new JsonObject(); response.addProperty("jsonrpc", "2.0"); response.add("id", request.get("id"));
      String method = request.get("method").getAsString();
      try {
        var params = request.getAsJsonObject("params");
        if (method.equals("ouroboros/prepareEnvironment")) response.add("result", prepare(params));
        else if (method.equals("ouroboros/putArtifact")) { put(params); response.add("result", new JsonObject()); }
        else if (method.equals("ouroboros/start")) {
          start(input, output, response);
          return;
        } else throw new IllegalArgumentException("Unsupported bootstrap method");
      } catch (IllegalArgumentException | IOException error) {
        var failure = new JsonObject(); failure.addProperty("code", -32602); failure.addProperty("message", error.getMessage());
        response.add("error", failure);
      }
      write(output, response);
    }
  }

  private JsonObject prepare(JsonObject params) throws IOException {
    if (manifest != null) throw new IllegalArgumentException("Environment already prepared");
    if (params.get("protocolVersion").getAsInt() != 1 || params.get("javaMajor").getAsInt() != Runtime.version().feature()
        || !engine.get("language").equals(params.get("language")) || !engine.get("engineVersion").equals(params.get("engineVersion"))
        || !params.get("environmentVersion").getAsString().matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Incompatible language host protocol or engine");
    var missing = new JsonArray();
    var entries = params.getAsJsonArray("artifacts");
    if (entries.size() > 4096) throw new IllegalArgumentException("Invalid artifact count");
    var declared = new LinkedHashMap<String, Long>();
    long total = 0;
    for (var entry : entries) {
      var item = entry.getAsJsonObject(); String digest = item.get("sha256").getAsString(); long size = item.get("size").getAsLong();
      if (!digest.matches("[a-f0-9]{64}") || size <= 0 || size > 268435456 || (total += size) > 1073741824L)
        throw new IllegalArgumentException("Artifact budget exceeded");
      if (declared.putIfAbsent(digest, size) != null) throw new IllegalArgumentException("Duplicate artifact");
    }
    // A host has one active environment; keep only its immutable artifacts, bounded by the manifest budget.
    try (var files = Files.list(storage)) {
      for (var path : files.filter(Files::isRegularFile).toList()) {
        if (!path.getFileName().toString().equals("ready") && !declared.containsKey(path.getFileName().toString().replaceFirst("\\.(jar|part)$", ""))) Files.delete(path);
      }
    }
    artifacts.putAll(declared);
    for (var artifact : declared.entrySet()) {
      String digest = artifact.getKey(); long size = artifact.getValue();
      Path path = storage.resolve(digest + ".jar");
      if (!Files.isRegularFile(path) || Files.size(path) != size || !hash(path).equals(digest)) {
        Files.deleteIfExists(path); Files.deleteIfExists(storage.resolve(digest + ".part")); missing.add(digest);
      }
    }
    manifest = params.deepCopy();
    var result = new JsonObject(); result.add("missing", missing); return result;
  }

  private void put(JsonObject params) throws IOException {
    String digest = params.get("sha256").getAsString();
    Long size = artifacts.get(digest);
    if (manifest == null || size == null) throw new IllegalArgumentException("Undeclared artifact");
    var path = storage.resolve(digest + ".part");
    long offset = params.get("offset").getAsLong();
    byte[] bytes = Base64.getDecoder().decode(params.get("data").getAsString());
    if (bytes.length == 0 || bytes.length > 131072 || offset != (Files.exists(path) ? Files.size(path) : 0)
        || offset + bytes.length > size) throw new IllegalArgumentException("Invalid artifact chunk");
    Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    if (offset + bytes.length == size) {
      if (!hash(path).equals(digest)) { Files.delete(path); throw new IllegalArgumentException("Artifact digest mismatch"); }
      Files.move(path, storage.resolve(digest + ".jar"), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void start(InputStream input, OutputStream output, JsonObject response) throws Exception {
    if (manifest == null) throw new IllegalArgumentException("Environment not prepared");
    for (var artifact : artifacts.entrySet()) {
      Path path = storage.resolve(artifact.getKey() + ".jar");
      if (!Files.isRegularFile(path) || Files.size(path) != artifact.getValue() || !hash(path).equals(artifact.getKey()))
        throw new IllegalArgumentException("Environment artifact incomplete");
    }
    Path workspace = Files.createTempDirectory(storage, "workspace-");
    try {
      Path classpath = workspace.resolve("classpath.json");
      var paths = new JsonArray();
      for (var artifact : manifest.getAsJsonArray("artifacts")) paths.add(storage.resolve(artifact.getAsJsonObject().get("sha256").getAsString() + ".jar").toAbsolutePath().toString());
      Files.writeString(classpath, JSON.toJson(paths));
      var command = new ArrayList<String>();
      for (var argument : engine.getAsJsonArray("command")) command.add(argument.getAsString()
          .replace("{workspace}", workspace.toString()).replace("{classpath}", classpath.toString()));
      var child = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
      try {
        var result = new JsonObject(); result.addProperty("documentRoot", workspace.toUri().toString()); response.add("result", result); write(output, response);
        var upstream = Thread.startVirtualThread(() -> {
          try {
            byte[] bytes = new byte[65536];
            for (int n; (n = input.read(bytes)) >= 0;) { child.getOutputStream().write(bytes, 0, n); child.getOutputStream().flush(); }
            child.getOutputStream().close();
          }
          catch (IOException error) { System.err.println("Language client disconnected: " + error); }
          finally { child.destroy(); }
        });
        try { child.getInputStream().transferTo(output); output.flush(); }
        finally { upstream.interrupt(); }
      } finally {
        child.destroy(); if (!child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) child.destroyForcibly();
      }
    } finally {
      try (var paths = Files.walk(workspace)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }
  }

  private static String hash(Path path) throws IOException {
    try (var input = Files.newInputStream(path)) {
      var digest = MessageDigest.getInstance("SHA-256"); byte[] bytes = new byte[65536];
      for (int n; (n = input.read(bytes)) >= 0;) digest.update(bytes, 0, n);
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
  }
  private static JsonObject read(InputStream input) throws IOException {
    var header = new StringBuilder();
    for (int c; (c = input.read()) >= 0;) {
      header.append((char)c);
      if (header.toString().endsWith("\r\n\r\n")) break;
      if (header.length() > 8192) throw new IOException("Oversized header");
    }
    if (header.isEmpty()) return null;
    int length = -1;
    for (var line : header.toString().split("\r\n")) if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
    if (length < 0 || length > 8_000_000) throw new IOException("Invalid frame size");
    byte[] body = input.readNBytes(length); if (body.length != length) throw new IOException("Truncated frame");
    return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
  }
  private static void write(OutputStream output, JsonObject message) throws IOException {
    byte[] body = JSON.toJson(message).getBytes(StandardCharsets.UTF_8);
    output.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); output.write(body); output.flush();
  }
}
