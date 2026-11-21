package com.ouroboros.logicflow.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ouroboros.logicflow.util.LogicFlowJson;

public class FlowCodeIntelligenceContractTest {
  @Test public void optionalContractsSurviveJsonAndBuilderCopies() throws Exception {
    var schema = new LinkedHashMap<String, Object>(Map.of("type", "object", "properties", Map.of("id", Map.of("type", "integer"))));
    var flow = FlowModelMeta.builder().name("contract").inputSchema(schema).outputSchema(schema).build();
    schema.put("type", "string");
    assertEquals("object", flow.getInputSchema().get("type"));
    var clone = flow.map(builder -> builder.inputSchema(Map.of("type", "number")));
    assertEquals("number", clone.getInputSchema().get("type"));
    assertEquals("object", flow.getInputSchema().get("type"));
    var restored = LogicFlowJson.tryToBean(FlowModelMeta.class, LogicFlowJson.OBJECT_MAPPER.writeValueAsString(flow)).get();
    assertEquals(flow.getInputSchema(), restored.getInputSchema());
    assertEquals(flow.getOutputSchema(), restored.getOutputSchema());
    assertTrue(FlowModelMeta.builder().inputSchema(null).outputSchema(null).build().getInputSchema().isEmpty());
    assertTrue(LogicFlowJson.tryToBean(FlowModelMeta.class, "{\"name\":\"old\"}").get().getOutputSchema().isEmpty());
  }
}
