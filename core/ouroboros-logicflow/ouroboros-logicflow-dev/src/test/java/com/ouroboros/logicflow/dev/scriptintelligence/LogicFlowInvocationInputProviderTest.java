package com.ouroboros.logicflow.dev.scriptintelligence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ouroboros.dev.codeintelligence.ReadOnlyMetadataView;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.script.intelligence.ScriptIntelligenceSnapshot;
import com.ouroboros.util.Json;

class LogicFlowInvocationInputProviderTest {
  @Test void actualCallerAnalysisIncludesUpstreamMappingAndExcludesDisconnectedOrDisabledCalls() {
    var provider = new LogicFlowInvocationInputProvider();
    var metadata = view("""
        {"name":"caller","entryNode":"validate","nodes":[
          {"name":"validate","type":"validate","schema":{"type":"object","properties":{"customer":{"type":"string"}}}},
          {"name":"map","type":"mapping","mapping":{"label":"$0.customer"}},
          {"name":"rpc","type":"CallRPC","modelName":"orders"},
          {"name":"disabled","type":"CallRPC","modelName":"orders","isEnabled":false},
          {"name":"disconnected","type":"CallRPC","modelName":"orders"}],
          "connections":[
            {"sourceNode":"validate","sourceOutputPort":0,"targetNode":"map","targetInputPort":0},
            {"sourceNode":"map","sourceOutputPort":0,"targetNode":"rpc","targetInputPort":0}]}
        """);
    var inferred = provider.infer("rpc-model", "orders", metadata).orElseThrow();
    assertEquals("string", inferred.property("label").type());
    assertFalse(inferred.properties().containsKey("customer"));
    assertTrue(provider.infer("rpc-model", "absent", metadata).isEmpty());
    assertTrue(provider.infer("other-resource", "orders", metadata).isEmpty());
  }

  @Test void knownCallWithUnknownInputIsReportedAsUnknownEvidence() {
    var result = new LogicFlowInvocationInputProvider().infer("rpc-model", "orders", view("""
        {"name":"caller","entryNode":"rpc","nodes":[{"name":"rpc","type":"CallRPC","modelName":"orders"}]}
        """));
    assertTrue(result.isPresent());
    assertTrue(result.orElseThrow().isUnknown());
    assertTrue(new LogicFlowInvocationInputProvider().infer("rpc-model", "orders", view("""
        {"name":"caller","isEnabled":false,"entryNode":"rpc","nodes":[{"name":"rpc","type":"CallRPC","modelName":"orders"}]}
        """)).isEmpty());
  }

  private ReadOnlyMetadataView view(String flow) {
    var metadata = new Metadata(); metadata.setName("caller"); metadata.setDetails(Json.toMap(flow));
    return new ReadOnlyMetadataView("app", ApplicationMetadataSnapshot.current(Map.of("logic-flow", List.of(metadata))), new ScriptIntelligenceSnapshot());
  }
}
