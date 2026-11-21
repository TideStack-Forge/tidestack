package com.ouroboros.dev.codeintelligence.language;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavascriptOutputContractTest {
  @TempDir Path directory;
  @Test void isolatedWorkerUsesContextBindingsAndRejectsStaleContracts() {
    var json = new ObjectMapper();
    try (var runtime = new LanguageServerRuntime(new JavascriptLanguageServerProvider(), directory, new LanguageEnvironment("d".repeat(64), 21, List.of()))) {
      var open = json.createObjectNode().put("method", "textDocument/didOpen"); open.putObject("params").putObject("textDocument").put("text", "return {name: $0.name, active: true}").put("version", 3);
      var context = json.createObjectNode().put("method", "ouroboros/context"); var snapshot = context.putObject("params").put("documentVersion", 3).putObject("snapshot");
      snapshot.putArray("globals").addObject().put("name", "$0").putObject("shape").putObject("schema").put("type", "object").putObject("properties").putObject("name").put("type", "string");
      var infer = json.createObjectNode().put("method", "ouroboros/inferOutput").put("id", "infer"); infer.putObject("params").put("documentVersion", 3).put("draftRevision", 8).putObject("textDocument");
      String session = UUID.randomUUID().toString();
      var result = runtime.exchange(session, List.of(open, context, infer)).join().getFirst();
      assertFalse(result.has("error"), result.toString()); assertEquals("string", result.at("/result/shape/schema/properties/name/type").asText());
      assertEquals("boolean", result.at("/result/shape/schema/properties/active/type").asText());
      infer.withObject("params").put("documentVersion", 2);
      var stale = runtime.exchange(session, List.of(infer)).join().getFirst(); assertTrue(stale.has("error"), stale.toString());
    }
  }
}
