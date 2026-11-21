package com.ouroboros.dev.codeintelligence.language;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import com.ouroboros.codeintelligence.type.TypeShape;

@EnabledIfSystemProperty(named = "groovy.language.distribution", matches = ".+")
class GroovyOutputContractTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  @TempDir Path directory;
  @Test void typedEditorModuleAlsoInfersJavaPropertiesGenericsAndBranchReturnContracts() throws Exception {
    Path classes = directory.resolve("extension"); Files.createDirectories(classes);
    Path source = classes.resolve("Customer.java");
    Files.writeString(source, "package extension; public class Customer { public String getName(){throw new AssertionError(\"must not execute\");} public java.util.List<Customer> getFriends(){return null;} }");
    assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString()));
    try (var runtime = new LanguageServerRuntime(new GroovyLanguageServerProvider(Path.of(System.getProperty("groovy.language.distribution"))),
        directory.resolve("runtime"), new LanguageEnvironment("a".repeat(64), 21, List.of(classes)))) {
      var customer = infer(runtime, "return new extension.Customer()");
      assertEquals("string", customer.property("name").type(), customer.toString());
      assertEquals("array", customer.property("friends").type());
      assertEquals("extension.Customer", customer.schema().path("x-javaType").asText());
      assertEquals("string", infer(runtime, "def person = new extension.Customer(); return [label: person.getName()]").property("label").type());
      assertEquals("string", infer(runtime, "def person = [name: 'Ada']; if (flag) return [rows: [person]]; return [active: true]").property("rows").element().property("name").type());
      assertEquals("boolean", infer(runtime, "def x = [old: 1]; if (flag) return [stop: true]; x = [ok: true]; x").property("ok").type());
      assertTrue(infer(runtime, "return unknownCall()").isUnknown());
      assertTrue(infer(runtime, "while (true) {}").isUnknown());
    }
  }
  private TypeShape infer(LanguageServerRuntime runtime, String source) throws Exception {
    String session = UUID.randomUUID().toString();
    try {
      var open = message("textDocument/didOpen", false); open.withObject("params").putObject("textDocument").put("languageId", "groovy").put("version", 1).put("text", source);
      var context = message("ouroboros/context", false); context.withObject("params").put("documentVersion", 1).putObject("snapshot").putArray("globals");
      var query = message("ouroboros/inferOutput", true); query.withObject("params").put("documentVersion", 1).put("draftRevision", 42).putObject("textDocument");
      var results = runtime.exchange(session, List.of(open, context, query)).get(60, java.util.concurrent.TimeUnit.SECONDS);
      var response = results.stream().filter(item -> item.has("id")).findFirst().orElseThrow();
      assertFalse(response.has("error"), response.toString());
      assertEquals(42, response.path("result").path("draftRevision").asInt());
      return new TypeShape(response.path("result").path("shape").path("schema"));
    } finally { runtime.closeSession(session).join(); }
  }
  private com.fasterxml.jackson.databind.node.ObjectNode message(String method, boolean request) {
    var result = JSON.createObjectNode().put("jsonrpc", "2.0").put("method", method); result.putObject("params");
    if (request) result.put("id", UUID.randomUUID().toString()); return result;
  }
}
