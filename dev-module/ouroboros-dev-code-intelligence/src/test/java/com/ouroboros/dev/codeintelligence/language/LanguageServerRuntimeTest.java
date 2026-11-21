package com.ouroboros.dev.codeintelligence.language;

import java.nio.file.Path;
import java.nio.file.Files;
import java.net.URLClassLoader;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LanguageServerRuntimeTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  @TempDir Path directory;

  @Test void rejectsInvalidSessionsAndReportsMissingDistribution() {
    try (var service = new LanguageServerRuntime(new GroovyLanguageServerProvider((Path) null), directory, new LanguageEnvironment("a".repeat(64), 21, List.of()))) {
      assertThrows(RuntimeException.class, () -> service.exchange("invalid", List.of(JSON.createObjectNode())).join());
      assertThrows(RuntimeException.class, () -> service.exchange(UUID.randomUUID().toString(), java.util.Collections.nCopies(17, JSON.createObjectNode())).join());
      assertTrue(assertThrows(RuntimeException.class, () -> service.exchange(UUID.randomUUID().toString(), List.of(JSON.createObjectNode())).join()).getMessage().contains("not installed"));
    }
  }
  @Test @EnabledIfSystemProperty(named = "groovy.language.distribution", matches = ".+")
  void usesActualApplicationExtensionClasspathAndKeepsSessionsIsolatedInOneProcess() throws Exception {
    Path classes = directory.resolve("extension"); Files.createDirectories(classes);
    Path source = classes.resolve("Customer.java");
    Files.writeString(source, "package extension; public class Customer { public String getDisplayName(){throw new AssertionError(\"must not execute\");} }");
    assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString()));
    try (var loader = new URLClassLoader(new java.net.URL[] { classes.toUri().toURL() }, getClass().getClassLoader());
         var service = new LanguageServerRuntime(new GroovyLanguageServerProvider(Path.of(System.getProperty("groovy.language.distribution"))), directory.resolve("runtime"), new LanguageEnvironment("a".repeat(64), 21, List.of(classes)))) {
      String first = UUID.randomUUID().toString(), second = UUID.randomUUID().toString();
      List<JsonNode> opened = service.exchange(first, List.of(message("initialize", "init", "{}"),
          message("textDocument/didOpen", null, "{\"textDocument\":{\"languageId\":\"groovy\",\"version\":1,\"text\":\"import extension.Customer\\nCustomer customer = null\\ncustomer.\"}}"))).get(40, java.util.concurrent.TimeUnit.SECONDS);
      assertTrue(opened.stream().anyMatch(m -> m.path("result").path("serverInfo").path("version").asText().equals("4.0.30")));
      long processCount = ProcessHandle.current().descendants().filter(p -> p.info().commandLine().orElse("").contains("launcher.jar")).count();
      assertEquals(1, processCount);
      var completed = service.exchange(first, List.of(message("textDocument/completion", "complete", "{\"textDocument\":{},\"position\":{\"line\":2,\"character\":9}}"))).join();
      assertTrue(completed.toString().contains("displayName"), completed.toString());
      service.exchange(second, List.of(message("textDocument/didOpen", null, "{\"textDocument\":{\"languageId\":\"groovy\",\"version\":1,\"text\":\"Integer customer = 1\\ncustomer.\"}}"))).join();
      var other = service.exchange(second, List.of(message("textDocument/completion", "other", "{\"textDocument\":{},\"position\":{\"line\":1,\"character\":9}}"))).join();
      assertFalse(other.toString().contains("displayName"));
      assertTrue(other.toString().contains("intValue"));
      service.closeSession(first).join();
      assertEquals(1, ProcessHandle.current().descendants().filter(p -> p.info().commandLine().orElse("").contains("launcher.jar")).count());
      service.closeSession(second).join();
    }
    assertEquals(0, ProcessHandle.current().descendants().filter(p -> p.info().commandLine().orElse("").contains("launcher.jar")).count());
  }

  private static JsonNode message(String method, String id, String params) throws Exception {
    var message = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method);
    if (id != null) message.put("id", id);
    message.set("params", JSON.readTree(params));
    return message;
  }
}
