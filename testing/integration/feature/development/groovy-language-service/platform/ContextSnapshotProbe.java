package com.ouroboros.testing.groovy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.codeintelligence.CodeContextRequest;
import com.ouroboros.dev.codeintelligence.service.CodeContextService;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.codeintelligence.type.JavaShapeIntrospector;
import com.ouroboros.script.intelligence.ScriptIntelligenceGlobal;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.logicflow.dev.scriptintelligence.LogicFlowCodeContextProvider;
import com.ouroboros.util.Json;

/** Produces the real platform contract through the canonical service and registered flow analyzers. */
public final class ContextSnapshotProbe {
  public static void main(String[] args) throws Exception {
    var javaBinding = new ScriptIntelligenceGlobal();
    javaBinding.setName("javaContext");
    javaBinding.setKind("variable");
    javaBinding.setShape(new JavaShapeIntrospector().describe(Class.forName("probe.ContextRoot", false, ContextSnapshotProbe.class.getClassLoader())));
    var environment = new ScriptIntelligenceSnapshot();
    environment.setGlobals(List.of(javaBinding));
    var metadata = new Metadata();
    metadata.setName("groovy");
    metadata.setDetails(Json.toMap(Json.toJsonString(environment)));
    var service = new CodeContextService(List.of(new LogicFlowCodeContextProvider()), List.of(app ->
        ApplicationMetadataSnapshot.current(Map.of("script-intelligence", List.of(metadata)))
            .withRuntimeIdentity(Map.of("appIncarnationId", "semantic-probe", "refreshRevision", 1))));
    for (String fieldType : List.of("string", "integer")) {
      var draft = Json.toBean(JsonNode.class, """
          {"name":"semantic-probe","entryNode":"validate","nodes":[
            {"name":"validate","type":"validate","schema":{"type":"object","properties":{
              "customers":{"type":"array","items":{"type":"object","properties":{
                "name":{"type":"%s"},"address":{"type":"object","properties":{"city":{"type":"string"}}}}}}}}},
            {"name":"mapping","type":"mapping","mapping":{
              "customers":"$0.customers","profile.label":"$0.customers[0].name"}},
            {"name":"script","type":"script","language":"groovy",
              "script":"throw new IllegalStateException('analysis must not execute this')",
              "inputPorts":[{"name":"mapped"}]}],
            "connections":[
              {"sourceNode":"validate","sourceOutputPort":0,"targetNode":"mapping","targetInputPort":0},
              {"sourceNode":"mapping","sourceOutputPort":0,"targetNode":"script","targetInputPort":0}]}
          """.formatted(fieldType));
      var request = new CodeContextRequest("groovy-semantic-probe", 1, 1, "logicflow.script", "groovy",
          new CodeContextRequest.Resource("logicflow", "semantic-probe"),
          new CodeContextRequest.Location("script", "/script"), draft);
      var response = service.describe("semantic-probe-app", request);
      Files.writeString(Path.of(args[0]).resolve("context-" + fieldType + ".json"), Json.toJsonString(response));
    }
  }
}
