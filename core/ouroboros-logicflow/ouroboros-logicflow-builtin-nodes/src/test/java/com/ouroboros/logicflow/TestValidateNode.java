package com.ouroboros.logicflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

import com.ouroboros.logicflow.model.DefaultFlowModel;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.util.LogicFlowJson;

public class TestValidateNode {

  @Test
  public void validateNodeAcceptsValidPayload() {
    var payload = LogicFlowJson.toMap("{\"name\":\"ouroboros\"}");
    var flowModel = FlowModelFactory.build(buildValidateMeta(), NodeReferenceCatalog.empty());

    var result = flowModel.execute(payload);

    assertTrue(result.isSuccess());
    assertEquals(payload, result.get());
  }

  @Test
  public void validateNodeRejectsInvalidPayloadWithSchemaDetails() {
    var flowModel = FlowModelFactory.build(buildValidateMeta(), NodeReferenceCatalog.empty());

    var result = flowModel.execute(LogicFlowJson.toMap("{\"age\": 1}"));

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage().contains("required"));
  }

  private FlowModelMeta buildValidateMeta() {
    Map<String, Object> schema = LogicFlowJson.toMap("{\n" +
        "  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\",\n" +
        "  \"type\": \"object\",\n" +
        "  \"properties\": {\n" +
        "    \"name\": {\n" +
        "      \"type\": \"string\"\n" +
        "    }\n" +
        "  },\n" +
        "  \"required\": [\"name\"]\n" +
        "}");
    return buildValidateMeta(schema);
  }

  @Test
  public void validateNodePassesThroughPayloadWithoutSchema() {
    var payload = Map.of("unchanged", true);
    var model = FlowModelFactory.build(buildValidateMeta(Map.of()), NodeReferenceCatalog.empty());

    assertEquals(payload, model.execute(payload).get());
  }

  @Test
  public void validateNodeReportsInvalidSchemaAsValidationFailure() {
    var model = FlowModelFactory.build(buildValidateMeta(Map.of("type", "string", "pattern", "[")), NodeReferenceCatalog.empty());

    var result = model.execute("sample");

    assertTrue(result.isFailure());
    assertTrue(result.getCause().getMessage(), result.getCause().getMessage().contains("$schema"));
  }

  private FlowModelMeta buildValidateMeta(Map<String, Object> schema) {
    var validateNodeMeta = FlowNodeMeta.builder()
        .name("validate")
        .type("validate")
        .extraProp("schema", schema)
        .build();
    return FlowModelMeta.builder()
        .name("validateFlow")
        .node(validateNodeMeta)
        .entryNode("validate")
        .exitNode("validate")
        .build();
  }
}
