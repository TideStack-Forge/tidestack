package com.ouroboros.logicflow.dev.scriptintelligence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.dev.metadata.ApplicationMetadataSnapshot;
import com.ouroboros.expression.intelligence.SpelTypeQuery;
import com.ouroboros.logicflow.authoring.analysis.*;
import com.ouroboros.logicflow.model.*;
import com.ouroboros.util.Json;

class ExtendedDataflowContractsTest {
  @Test void undeclaredSubflowOutputIsInferredFromTheCallersActualInputAndRecursionIsBounded() {
    var map = FlowNodeMeta.builder().name("map").type("mapping").extraProp("mapping", Map.of("label", "$0.name")).build();
    var sub = FlowModelMeta.builder().name("sub").entryNode(map).exitNode("map").build();
    var recursive = FlowModelMeta.builder().name("recursive").entryNode(FlowNodeMeta.builder().name("self").type("callFlow")
        .extraProp("flowName", "recursive").build()).exitNode("self").build();
    var contracts = new MetadataFlowTypeContracts(ApplicationMetadataSnapshot.unavailable(), Map.of("logic-flow", Map.of(
        "sub", Json.toBean(JsonNode.class, Json.toJsonString(sub)), "recursive", Json.toBean(JsonNode.class, Json.toJsonString(recursive)))));
    var input = TypeShape.object(Map.of("name", TypeShape.scalar("string")), "map");
    assertEquals("string", contracts.callFlow("sub", List.of(input), Map.of(), new SpelTypeQuery()).property("label").type());
    var nested = FlowNodeMeta.builder().name("call").type("callFlow").extraProp("flowName", "sub")
        .extraProp("executeForEachItem", true).extraProp("resultWrapperKey", "rows").build();
    var result = new ReferencedNodeAnalyzer().analyze(new NodeAnalysisInput(nested, List.of(PortShape.always(TypeShape.array(input))), Map.of(), contracts), new SpelTypeQuery());
    assertEquals("string", result.outputs().get(0).shape().property("rows").element().property("label").type());
    assertTrue(contracts.callFlow("recursive", List.of(input), Map.of(), new SpelTypeQuery()).isUnknown());
  }

  @Test void excelConfigurationDescribesRowsSheetsAndErrorResultsWithoutReadingFiles() {
    var node = FlowNodeMeta.builder().name("excel").type("readExcel").extraProp("useCustomHeaders", true)
        .extraProp("customHeaders", List.of("sku", "quantity")).build();
    var rows = excel(node);
    assertTrue(rows.element().properties().containsKey("sku"));
    assertTrue(rows.element().required().contains("quantity"));
    assertFalse(excel(node.map(builder -> builder.extraProp("skipEmptyColumns", true))).element().required().contains("quantity"));
    var sheets = excel(node.map(builder -> builder.extraProp("sheetSelectionMode", "All").extraProp("multiSheetResultFormat", "Map")));
    assertTrue(sheets.property("unknownSheetName").element().properties().containsKey("sku"));
    assertEquals("array", excel(node.map(builder -> builder.extraProp("sheetDataFormat", "ListOfList"))).element().type());
    assertEquals("java", excel(node.map(builder -> builder.extraProp("sheetDataFormat", "SheetData"))).representation());
    assertTrue(excel(node.map(builder -> builder.extraProp("errorHandlingStrategy", "Default"))).schema().path("anyOf").isArray());
    var exception = FlowNodeMeta.builder().name("fail").type("ouroborosExceptions").build();
    assertTrue(new ExtraNodeAnalyzer().analyze(new NodeAnalysisInput(exception, List.of(), Map.of()), new SpelTypeQuery()).outputs().isEmpty());
  }

  @Test void scriptOutputContractsReachDownstreamEditorsThroughTheNodeSpi() {
    for (var language : List.of("groovy", "javascript")) {
      var source = language.equals("groovy") ? "return [customer: [name: $0.name]]" : "return {customer: {name: $0.name}};";
      var script = FlowNodeMeta.builder().name("producer").type("script").extraProp("script", source).extraProp("language", language).build();
      var consumer = FlowNodeMeta.builder().name("consumer").type("script").build();
      var flow = FlowModelMeta.builder().entryNode(script).node(consumer).connection(FlowConnectionMeta.builder().sourceNode("producer")
          .sourceOutputPort(0).targetNode("consumer").targetInputPort(0).build()).build();
      var inputs = List.of(TypeShape.object(Map.of("name", TypeShape.scalar("string")), "map"));
      var analyzer = new LogicFlowDataflowAnalyzer();
      var unavailable = analyzer.analyze(flow, inputs, Map.of(), new SpelTypeQuery());
      assertTrue(unavailable.nodes().get("consumer").codeScopes().get("/script").bindings().get("$0").isUnknown());
      var contracts = new FlowTypeContracts() {
        public TypeShape find(String resourceType, String name, String role) { return TypeShape.unknown("missing-contract"); }
        public TypeShape scriptOutput(String requestedLanguage, String requestedSource, Map<String, TypeShape> bindings) {
          assertEquals(language, requestedLanguage);
          assertEquals(source, requestedSource);
          assertEquals("string", bindings.get("$0").property("name").type());
          return TypeShape.object(Map.of("customer", bindings.get("$0")), "map");
        }
      };
      var result = analyzer.analyze(flow, inputs, Map.of(), new SpelTypeQuery(), contracts);
      assertEquals("string", result.nodes().get("consumer").codeScopes().get("/script").bindings().get("$0").property("customer").property("name").type());
    }
  }

  private TypeShape excel(FlowNodeMeta node) {
    return new ExtraNodeAnalyzer().analyze(new NodeAnalysisInput(node, List.of(), Map.of()), new SpelTypeQuery()).outputs().get(0).shape();
  }

  @Test void subflowSuccessDoesNotIncludeDisabledOrUnreachableReturnPaths() {
    var raw = Json.toBean(JsonNode.class, """
        {"name":"sub","entryNode":"entry","exitNode":"exit","nodes":[
          {"name":"entry","type":"const","value":{"live":"yes"}},
          {"name":"exit","type":"return"},
          {"name":"disabled","type":"mapping","isEnabled":false,"mapping":{"bad":1}},
          {"name":"deadReturn","type":"return"}],
          "connections":[
            {"sourceNode":"entry","sourceOutputPort":0,"targetNode":"exit","targetInputPort":0},
            {"sourceNode":"entry","sourceOutputPort":0,"targetNode":"disabled","targetInputPort":0},
            {"sourceNode":"disabled","sourceOutputPort":0,"targetNode":"deadReturn","targetInputPort":0}]}
        """);
    var contracts = new MetadataFlowTypeContracts(ApplicationMetadataSnapshot.unavailable(), Map.of("logic-flow", Map.of("sub", raw)));
    var result = contracts.callFlow("sub", List.of(), Map.of(), new SpelTypeQuery());
    assertEquals("string", result.property("live").type());
    assertFalse(result.properties().containsKey("bad"));
  }
}
