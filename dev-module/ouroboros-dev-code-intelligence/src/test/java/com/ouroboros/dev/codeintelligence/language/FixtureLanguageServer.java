package com.ouroboros.dev.codeintelligence.language;

import java.io.BufferedInputStream;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** A second language's protocol fixture, not a production semantic engine. */
public final class FixtureLanguageServer {
  public static void main(String[] args) throws Exception {
    var json = new ObjectMapper();
    var input = new BufferedInputStream(System.in);
    String document = "", projected = "";
    while (true) {
      var header = new StringBuilder();
      for (int c; (c = input.read()) >= 0;) { header.append((char) c); if (header.toString().endsWith("\r\n\r\n")) break; }
      if (header.isEmpty()) return;
      int length = Integer.parseInt(header.toString().split(":")[1].trim());
      var message = json.readTree(input.readNBytes(length));
      String method = message.path("method").asText();
      if (method.equals("textDocument/didOpen")) {
        document = message.path("params").path("textDocument").path("uri").asText();
        var notification = json.createObjectNode().put("jsonrpc", "2.0").put("method", "textDocument/publishDiagnostics");
        var params = notification.putObject("params").put("uri", document).put("version", 1);
        var diagnostic = params.putArray("diagnostics").addObject().put("message", "fixture diagnostic").put("severity", 2);
        var range = diagnostic.putObject("range");
        range.putObject("start").put("line", 0).put("character", 0);
        range.putObject("end").put("line", 0).put("character", 1);
        byte[] body = json.writeValueAsBytes(notification);
        System.out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        System.out.write(body); System.out.flush();
      }
      if (method.equals("workspace/didChangeConfiguration")) projected = message.path("params").path("field").asText();
      if (!message.has("id")) continue;
      ObjectNode response = json.createObjectNode().put("jsonrpc", "2.0"); response.set("id", message.get("id"));
      switch (method) {
        case "initialize" -> {
          if (args[0].equals("fixture-failed-init")) response.putObject("error").put("code", -32603).put("message", "fixture initialization failure");
          else response.putObject("result").putObject("capabilities").putObject("completionProvider").putArray("triggerCharacters").add(":");
        }
        case "textDocument/completion" -> response.putArray("result").addObject().put("label", projected).put("detail", args[0] + ":" + document);
        case "test/terminate" -> { return; }
        default -> response.putObject("error").put("code", -32601).put("message", "Method not found: " + method);
      }
      byte[] body = json.writeValueAsBytes(response);
      System.out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      System.out.write(body); System.out.flush();
    }
  }
}
