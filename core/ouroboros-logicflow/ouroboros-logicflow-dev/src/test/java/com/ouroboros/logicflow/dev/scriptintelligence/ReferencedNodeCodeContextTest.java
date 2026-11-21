package com.ouroboros.logicflow.dev.scriptintelligence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.authoring.analysis.*;
import com.ouroboros.logicflow.model.FlowNodeMeta;

class ReferencedNodeCodeContextTest {
  private final TypeShape record = TypeShape.object(Map.of("id", TypeShape.scalar("integer")), "map");
  @Test void builtinReferencesAreComposedByTheOfficialNodeAnalyzerSpi() {
    assertTrue(LogicFlowDataflowAnalyzer.discoveredAnalyzers().stream().anyMatch(ReferencedNodeAnalyzer.class::isInstance));
    assertEquals("integer", output("insertData", Map.of()).property("id").type());
    assertEquals("array", output("searchData", Map.of()).type());
    assertEquals("integer", output("getOneData", Map.of()).property("id").type());
    assertEquals("array", output("pagedSearchData", Map.of("itemsKey", "rows", "totalKey", "count")).property("rows").type());
    assertEquals("integer", output("countData", Map.of("resultWrapperKey", "count")).property("count").type());
    assertEquals("integer", output("deleteData", Map.of()).type());
    assertEquals("any", output("deleteData", Map.of("returnDeletedData", true)).type());
    assertEquals("integer", output("partialUpdateData", Map.of()).type());
    assertEquals("object", output("CallRPC", Map.of("modelName", "rpc")).type());
    assertEquals("array", output("callFlow", Map.of("flowName", "sub", "executeForEachItem", true)).type());
    assertTrue(output("normalizeWhere", Map.of()).isUnknown());
  }
  private TypeShape output(String type, Map<String, Object> props) {
    var builder = FlowNodeMeta.builder().name("node").type(type).extraProp("dataModel", "Order");
    props.forEach(builder::extraProp);
    var input = new NodeAnalysisInput(builder.build(), List.of(), Map.of(), (resource, name, role) -> record);
    return new ReferencedNodeAnalyzer().analyze(input, (expression, bindings) -> TypeShape.unknown("unused")).outputs().get(0).shape();
  }
}
