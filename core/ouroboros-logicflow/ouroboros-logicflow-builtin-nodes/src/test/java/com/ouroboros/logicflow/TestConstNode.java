package com.ouroboros.logicflow;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;

import org.junit.Test;

import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.DefaultFlowModel;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.support.FlowModelAssertions;
import com.ouroboros.logicflow.util.LogicFlowJson;

public class TestConstNode {
  // 最简单的常量节点
  @Test
  public void testSimpleConstNode() {
    var constNodeMeta = FlowNodeMeta.builder()
        .name("const")
        .type("const")
        .extraProp("dataType", "string")
        .extraProp("value", "hello")
        .build();
    var flowModelMeta = FlowModelMeta.builder()
        .name("testConst")
        .node(constNodeMeta)
        .entryNode("const")
        .exitNode("const")
        .build();
    var flowModel = FlowModelFactory.build(flowModelMeta, NodeReferenceCatalog.empty());
    var result = flowModel.execute();
    assertTrue("执行失败", result.isSuccess());
    assertEquals("执行结果不正确", "hello", result.get());
  }

  // 常量节点增加验证字段
  @Test
  public void testSchemaValidatedConstNode() {
    var schema = new HashMap<String, Object>() {{
      put("type", "integer");
    }};
    var constNodeMeta = FlowNodeMeta.builder()
        .name("const")
        .type("const")
        .extraProp("dataType", "integer")
        .extraProp("value", 123)
        .extraProp("schema", schema)
        .build();
    var flowModelMeta = FlowModelMeta.builder()
        .name("testConst")
        .node(constNodeMeta)
        .entryNode("const")
        .exitNode("const")
        .build();
    var flowModel = FlowModelFactory.build(flowModelMeta, NodeReferenceCatalog.empty());
    var result = flowModel.execute();
    assertTrue("执行失败", result.isSuccess());
    assertEquals("执行结果不正确", 123, result.get());
  }

  // ConstNode 当前不会基于 schema 在设计阶段做失效判定
  @Test
  public void testSchemaAnnotatedMapConstNode() {
    var schema = LogicFlowJson.toMap("{\n" +
        "  \"type\": \"object\",\n" +
        "  \"title\": \"title\",\n" +
        "  \"properties\": {\n" +
        "    \"field_1\": {\n" +
        "      \"type\": \"string\"\n" +
        "    },\n" +
        "    \"field_2\": {\n" +
        "      \"type\": \"string\"\n" +
        "    }\n" +
        "  },\n" +
        "  \"required\": [\n" +
        "    \"field_1\",\n" +
        "    \"field_2\"\n" +
        "  ]\n" +
        "}");
    var value1 = LogicFlowJson.toMap("{\n" +
        "  \"field_1\": \"value_1\",\n" +
        "  \"field_2\": \"value_2\"\n" +
        "}");
    var value2 = LogicFlowJson.toMap("{\n" +
        "  \"field_2\": \"value_2\",\n" +
        "  \"field_3\": \"value_3\"\n" +
        "}");
    var constNodeMeta1 = FlowNodeMeta.builder()
        .name("const")
        .type("const")
        .extraProp("dataType", "map")
        .extraProp("value", value1)
        .extraProp("schema", schema)
        .build();
    var constNodeMeta2 = FlowNodeMeta.builder()
        .name("const")
        .type("const")
        .extraProp("dataType", "map")
        .extraProp("value", value2)
        .extraProp("schema", schema)
        .build();
    var flowModelMeta1 = FlowModelMeta.builder()
        .name("testConst")
        .node(constNodeMeta1)
        .entryNode("const")
        .exitNode("const")
        .build();
    var flowModelMeta2 = FlowModelMeta.builder()
        .name("testConst")
        .node(constNodeMeta2)
        .entryNode("const")
        .exitNode("const")
        .build();
    var flowModel1 = FlowModelFactory.build(flowModelMeta1, NodeReferenceCatalog.empty());
    var result1 = flowModel1.execute();
    assertTrue("执行失败", result1.isSuccess());
    assertEquals("执行结果不正确", value1, result1.get());

    var flowModel2 = FlowModelFactory.build(flowModelMeta2, NodeReferenceCatalog.empty());
    assertTrue("流程应当可用", flowModel2.isValid());
    var result2 = flowModel2.execute();
    assertTrue("应当执行成功", result2.isSuccess());
    assertEquals("执行结果不正确", value2, result2.get());
  }

  @Test
  public void testConstNodeConvertsExplicitScalarTypes() {
    assertEquals(123, executeConst("integer", 123));
    assertEquals(9223372036854775807L, executeConst("long", "9223372036854775807"));
    assertEquals(12.5F, ((Float) executeConst("float", 12.5)).floatValue(), 0.0001F);
    assertEquals(12.5D, ((Double) executeConst("double", 12.5)).doubleValue(), 0.0001D);
    assertEquals(true, executeConst("boolean", true));
  }

  @Test
  public void testConstNodeConvertsExplicitTemporalTypes() {
    assertEquals(LocalDate.of(2024, 2, 29), executeConst("date", "2024-02-29"));
    assertEquals(LocalDateTime.of(2024, 2, 29, 12, 0), executeConst("datetime", "2024-02-29T12:00:00"));
    assertEquals(LocalDateTime.of(2024, 2, 29, 12, 0), executeConst("datetime", "2024-02-29 12:00:00"));
    assertEquals(LocalTime.of(12, 59, 59), executeConst("time", "12:59:59"));
  }

  @Test
  public void testConstNodeRejectsValuesOutsideExplicitType() {
    assertInvalidConst("integer", 12.5);
    assertInvalidConst("integer", 2147483648L);
    assertInvalidConst("long", 12.5);
    assertInvalidConst("boolean", "yes");
    assertInvalidConst("date", "2026-02-29");
    assertInvalidConst("datetime", "2024-02-29 25:00:00");
    assertInvalidConst("time", "25:00:00");
    assertInvalidConst("array", LogicFlowJson.toMap("{\"a\":1}"));
    assertInvalidConst("map", LogicFlowJson.toList("[1,2]"));
  }

  private Object executeConst(String dataType, Object value) {
    var constNodeMeta = FlowNodeMeta.builder()
        .name("const")
        .type("const")
        .extraProp("dataType", dataType)
        .extraProp("value", value)
        .build();
    var flowModelMeta = FlowModelMeta.builder()
        .name("testConst")
        .node(constNodeMeta)
        .entryNode("const")
        .exitNode("const")
        .build();
    var flowModel = FlowModelFactory.build(flowModelMeta, NodeReferenceCatalog.empty());
    var result = flowModel.execute();
    assertTrue("执行失败", result.isSuccess());
    return result.get();
  }

  private void assertInvalidConst(String dataType, Object value) {
    var constNodeMeta = FlowNodeMeta.builder()
        .name("const")
        .type("const")
        .extraProp("dataType", dataType)
        .extraProp("value", value)
        .build();
    var flowModelMeta = FlowModelMeta.builder()
        .name("testConst")
        .node(constNodeMeta)
        .entryNode("const")
        .exitNode("const")
        .build();
    var flowModel = FlowModelFactory.build(flowModelMeta, NodeReferenceCatalog.empty());
    var error = FlowModelAssertions.assertNodeDesignError(flowModel);
    assertTrue("失败原因应当是常量值设计错误", error instanceof NodeDesignException);
    assertEquals("value", error.getFieldPath());
  }
}
