package com.ouroboros.businessmodel.dev;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.expression.intelligence.SpelTypeQuery;
import com.ouroboros.logicflow.authoring.analysis.*;
import com.ouroboros.logicflow.model.*;

class BusinessModelNodeAnalyzerTest {
  @Test void synchronizedRelationsReachFollowingScriptsWithNestedChildrenAndNoInventedOwnerWrapper() {
    var sync = FlowNodeMeta.builder().name("sync").type("syncChildCollections").extraProp("plans", List.of(Map.of(
        "relationName", "lines", "childDataModelName", "Line", "children", List.of(Map.of("relationName", "tags", "childDataModelName", "Tag"))))).build();
    var consumer = FlowNodeMeta.builder().name("after").type("script").build();
    var flow = FlowModelMeta.builder().entryNode(sync).node(consumer).connection(FlowConnectionMeta.builder().sourceNode("sync")
        .sourceOutputPort(0).targetNode("after").targetInputPort(0).build()).build();
    FlowTypeContracts contracts = (type, name, role) -> TypeShape.object(Map.of(name.equals("Line") ? "sku" : "label", TypeShape.scalar("string")), "map");
    var analysis = new LogicFlowDataflowAnalyzer().analyze(flow, List.of(), Map.of(), new SpelTypeQuery(), contracts);
    var result = analysis.nodes().get("after").codeScopes().get("/script").bindings().get("$0");
    assertEquals("string", result.property("lines").element().property("sku").type());
    assertEquals("string", result.property("lines").element().property("tags").element().property("label").type());
    assertTrue(result.property("persistedOwner").isUnknown());
    assertFalse(result.required().contains("lines"));
    assertFalse(analysis.diagnostics().stream().anyMatch(issue -> issue.code().equals("missing-analyzer")));
  }

  @Test void eventProducerReturnsTheOccurrenceEnvelopeAndNotTheInput() {
    var node = FlowNodeMeta.builder().name("event").type("emitBusinessEvent").build();
    var input = TypeShape.object(Map.of("subjectId", TypeShape.scalar("integer"), "payload", TypeShape.object(Map.of("approved", TypeShape.scalar("boolean")), "map")), "map");
    var result = new BusinessModelNodeAnalyzer().analyze(new NodeAnalysisInput(node, List.of(PortShape.always(input)), Map.of()), new SpelTypeQuery()).outputs().get(0).shape();
    assertEquals("string", result.property("eventId").type());
    assertEquals("integer", result.property("subject").property("id").type());
    assertEquals("boolean", result.property("payload").property("approved").type());
    assertTrue(result.property("subjectId").isUnknown());
  }
}
