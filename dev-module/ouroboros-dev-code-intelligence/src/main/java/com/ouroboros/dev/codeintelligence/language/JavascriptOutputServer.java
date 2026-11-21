package com.ouroboros.dev.codeintelligence.language;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.codeintelligence.analysis.JavascriptOutputInference;

/** Isolated algorithm worker, same negotiated output protocol as the Groovy semantic engine. */
public final class JavascriptOutputServer {
  private static final ObjectMapper JSON = new ObjectMapper();
  private record Document(String source, int version, Map<String, TypeShape> bindings) {}
  public static void main(String[] args) throws Exception {
    var documents = new HashMap<String, Document>();
    var parser = new JavascriptOutputInference();
    var input = new BufferedInputStream(System.in);
    while (true) {
      var header = new StringBuilder();
      for (int c; (c = input.read()) >= 0;) { header.append((char)c); if (header.toString().endsWith("\r\n\r\n")) break; if (header.length() > 8192) throw new IOException("Oversized LSP header"); }
      if (header.isEmpty()) return;
      int length = -1;
      for (var line : header.toString().split("\r\n")) if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
      if (length < 0 || length > 8_000_000) throw new IOException("Invalid LSP frame");
      byte[] bytes = input.readNBytes(length); if (bytes.length != length) throw new IOException("Truncated LSP frame");
      var message = JSON.readTree(bytes); var params = message.path("params");
      String method = message.path("method").asText(); String uri = params.path("textDocument").path("uri").asText();
      var response = JSON.createObjectNode().put("jsonrpc", "2.0"); response.set("id", message.get("id"));
      try {
        switch (method) {
          case "initialize" -> response.putObject("result").putObject("capabilities").putObject("experimental").putObject("ouroboros").put("protocolVersion", 1).put("inferOutput", true);
          case "textDocument/didOpen" -> documents.put(uri, new Document(params.path("textDocument").path("text").asText(), params.path("textDocument").path("version").asInt(), Map.of()));
          case "ouroboros/context" -> {
            uri = params.path("uri").asText(); var old = Objects.requireNonNull(documents.get(uri), "Unknown document");
            var bindings = new LinkedHashMap<String, TypeShape>();
            for (var binding : params.path("snapshot").path("globals")) bindings.put(binding.path("name").asText(), new TypeShape(binding.path("shape").path("schema")));
            documents.put(uri, new Document(old.source(), old.version(), bindings));
          }
          case "ouroboros/inferOutput" -> {
            var document = Objects.requireNonNull(documents.get(uri), "Unknown document");
            if (params.path("documentVersion").asInt() != document.version()) throw new IllegalArgumentException("Stale output document");
            var result = response.putObject("result").put("documentVersion", document.version()).put("draftRevision", params.path("draftRevision").asLong());
            result.set("shape", JSON.valueToTree(parser.inferOutput(document.source(), document.bindings())));
          }
          case "textDocument/didClose" -> documents.remove(uri);
          case "initialized" -> { }
          default -> throw new IllegalArgumentException("Unsupported method");
        }
      } catch (RuntimeException error) { response.putObject("error").put("code", -32602).put("message", error.getMessage()); }
      if (message.has("id")) {
        byte[] body = JSON.writeValueAsBytes(response);
        System.out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII)); System.out.write(body); System.out.flush();
      }
    }
  }
}
