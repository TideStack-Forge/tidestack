package com.ouroboros.logicflow.dev.scriptintelligence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.*;
import com.ouroboros.dev.codeintelligence.*;
import com.ouroboros.dev.codeintelligence.service.CodeContextService;
import com.ouroboros.dev.codeintelligence.language.*;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "groovy.language.distribution", matches = ".+")
class GroovyDataflowContractTest {
  @TempDir Path directory;
  @Test void semanticOutputPropagatesAcrossScriptsValidationAndMappingWithoutExecutingGetters() throws Exception {
    Path classes = directory.resolve("extension"); Files.createDirectories(classes);
    Path source = classes.resolve("Customer.java");
    Files.writeString(source, "package extension; public class Customer { public String getName(){ throw new AssertionError(\"never execute\"); } }");
    assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString()));
    var environment = new LanguageEnvironment("e".repeat(64), 21, List.of(classes));
    try (var services = new EditorLanguageServices(List.of(new GroovyLanguageServerProvider(Path.of(System.getProperty("groovy.language.distribution")))), directory.resolve("pool"), 1, java.time.Duration.ofMinutes(1), java.time.Clock.systemUTC())) {
      var output = new LanguageScriptOutputAnalysis(services, (app, identity) -> CompletableFuture.completedFuture(environment));
      var calls = new ArrayList<ScriptAnalysisQuery>();
      var service = new CodeContextService(List.of(new LogicFlowCodeContextProvider()), List.of(app -> ApplicationMetadataSnapshot.current(Map.of())),
          language -> new ScriptIntelligenceSnapshot(), (app, snapshot, query, request) -> { calls.add(query); return output.infer(app, snapshot, query, request); });
      var draft = new ObjectMapper().readTree("""
          {"name":"multi-hop","entryNode":"produce","nodes":[
            {"name":"produce","type":"script","language":"groovy","script":"return [customer: new extension.Customer()]"},
            {"name":"validate","type":"validate","schema":{}},
            {"name":"map","type":"mapping","mapping":{"label":"$0.customer.name"}},
            {"name":"transform","type":"script","language":"groovy","script":"return [display: $0.label]"},
            {"name":"current","type":"script","language":"groovy","script":""}],
            "connections":[
              {"sourceNode":"produce","sourceOutputPort":0,"targetNode":"validate","targetInputPort":0},
              {"sourceNode":"validate","sourceOutputPort":0,"targetNode":"map","targetInputPort":0},
              {"sourceNode":"map","sourceOutputPort":0,"targetNode":"transform","targetInputPort":0},
              {"sourceNode":"transform","sourceOutputPort":0,"targetNode":"current","targetInputPort":0}]}
          """);
      var request = new CodeContextRequest("multi-hop", 3, 9, "logicflow.script", "groovy", new CodeContextRequest.Resource("logicflow", "multi-hop"), new CodeContextRequest.Location("current", "/script"), draft);
      var result = service.describe("app", request);
      var input = result.snapshot().getGlobals().stream().filter(binding -> "$0".equals(binding.getName())).findFirst().orElseThrow().getShape();
      assertEquals("string", input.property("display").type(), input.toString());
      assertTrue(calls.stream().anyMatch(query -> query.source().contains("$0.label") && "string".equals(query.bindings().get("$0").property("label").type())));
      // A changed upstream source yields a new contract and replaces, rather than merges, stale fields.
      ((com.fasterxml.jackson.databind.node.ObjectNode) draft.path("nodes").get(3)).put("script", "return [changed: $0.label]");
      var changed = service.describe("app", new CodeContextRequest("multi-hop", 4, 10, request.codeSite(), request.language(), request.resource(), request.location(), draft));
      var next = changed.snapshot().getGlobals().stream().filter(binding -> "$0".equals(binding.getName())).findFirst().orElseThrow().getShape();
      assertEquals("string", next.property("changed").type()); assertFalse(next.properties().containsKey("display"));
    }
  }
}
