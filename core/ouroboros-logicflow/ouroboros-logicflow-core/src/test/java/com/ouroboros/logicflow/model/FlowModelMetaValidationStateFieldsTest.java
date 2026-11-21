package com.ouroboros.logicflow.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import com.ouroboros.logicflow.util.LogicFlowJson;

public class FlowModelMetaValidationStateFieldsTest {
  @Test
  public void builderShouldPopulateValidationSnapshotFields() {
    var validatedAt = LocalDateTime.of(2026, 3, 14, 10, 30, 45);
    var model = FlowModelMeta.builder()
        .name("validation-demo")
        .validationSummary("存在 2 条校验错误")
        .validationErrors(Arrays.asList("缺少成功终点", "存在不可达节点"))
        .validationDiagnostics(Arrays.asList(
            diagnostic("missing_successful_terminal", "缺少成功终点"),
            diagnostic("unreachable_nodes", "存在不可达节点", "orphan-a", "orphan-b")))
        .lastValidatedTime(validatedAt)
        .build();

    assertEquals(FlowModelValidationState.INVALID, model.getValidationState());
    assertEquals("存在 2 条校验错误", model.getValidationSummary());
    assertEquals(Arrays.asList("缺少成功终点", "存在不可达节点"), model.getValidationErrors());
    assertEquals(2, model.getValidationDiagnostics().size());
    assertEquals("missing_successful_terminal", model.getValidationDiagnostics().get(0).getDiagnosticKind());
    assertEquals("缺少成功终点", model.getValidationDiagnostics().get(0).getMessage());
    assertEquals(Collections.emptyList(), model.getValidationDiagnostics().get(0).getNodeNames());
    assertEquals(Arrays.asList("orphan-a", "orphan-b"), model.getValidationDiagnostics().get(1).getNodeNames());
    assertEquals(validatedAt, model.getLastValidatedTime());
  }

  @Test
  public void cloneShouldPreserveValidationSnapshotFieldsUsingDefensiveCopy() {
    var original = FlowModelMeta.builder()
        .name("validation-demo")
        .validationSummary("存在 1 条校验错误")
        .validationErrors(Arrays.asList("入口节点不存在"))
        .validationDiagnostics(Arrays.asList(
            diagnostic("node_design_error", "节点构造错误", "scriptNode")))
        .lastValidatedTime(LocalDateTime.of(2026, 3, 14, 11, 0, 0))
        .build();

    var cloned = original.clone();

    assertEquals(original.getValidationState(), cloned.getValidationState());
    assertEquals(original.getValidationSummary(), cloned.getValidationSummary());
    assertEquals(original.getValidationErrors(), cloned.getValidationErrors());
    assertEquals(original.getLastValidatedTime(), cloned.getLastValidatedTime());
    assertEquals(1, cloned.getValidationDiagnostics().size());
    assertEquals(original.getValidationDiagnostics().get(0).getDiagnosticKind(),
        cloned.getValidationDiagnostics().get(0).getDiagnosticKind());
    assertEquals(original.getValidationDiagnostics().get(0).getMessage(),
        cloned.getValidationDiagnostics().get(0).getMessage());
    assertEquals(original.getValidationDiagnostics().get(0).getNodeNames(),
        cloned.getValidationDiagnostics().get(0).getNodeNames());
    assertNotNull(cloned.getValidationErrors());
    assertNotNull(cloned.getValidationDiagnostics());
    assertTrue(original.getValidationErrors() != cloned.getValidationErrors());
    assertTrue(original.getValidationDiagnostics() != cloned.getValidationDiagnostics());
    assertTrue(original.getValidationDiagnostics().get(0) != cloned.getValidationDiagnostics().get(0));
  }

  @Test
  public void oldModelJsonShouldKeepValidationSnapshotFieldsOptional() {
    var json = "{\n"
        + "  \"name\": \"legacy-flow\",\n"
        + "  \"description\": \"legacy\",\n"
        + "  \"entryNode\": \"start\",\n"
        + "  \"nodes\": [],\n"
        + "  \"connections\": [],\n"
        + "  \"isEnabled\": true\n"
        + "}";

    var model = LogicFlowJson.tryToBean(FlowModelMeta.class, json).get();

    assertNull(model.getValidationState());
    assertNull(model.getValidationSummary());
    assertNotNull(model.getValidationErrors());
    assertNotNull(model.getValidationDiagnostics());
    assertTrue(model.getValidationErrors().isEmpty());
    assertTrue(model.getValidationDiagnostics().isEmpty());
    assertNull(model.getLastValidatedTime());
  }

  @Test
  public void jsonShouldPreserveFormalNodePositions() {
    var json = "{\n"
        + "  \"name\": \"layout-flow\",\n"
        + "  \"description\": \"layout\",\n"
        + "  \"nodes\": [],\n"
        + "  \"connections\": [],\n"
        + "  \"nodePositions\": {\n"
        + "    \"scriptNode\": { \"x\": 620.5, \"y\": 240.0 }\n"
        + "  }\n"
        + "}";

    var model = LogicFlowJson.tryToBean(FlowModelMeta.class, json).get();

    assertNotNull(model.getNodePositions());
    assertEquals(1, model.getNodePositions().size());
    assertEquals(Double.valueOf(620.5), model.getNodePositions().get("scriptNode").getX());
    assertEquals(Double.valueOf(240.0), model.getNodePositions().get("scriptNode").getY());
  }

  @Test
  public void cloneShouldPreserveNodePositionsUsingDefensiveCopy() {
    var original = FlowModelMeta.builder()
        .name("layout-demo")
        .nodePositions(Collections.singletonMap(
            "scriptNode",
            FlowNodePositionMeta.builder().x(680.0).y(280.0).build()))
        .build();

    var cloned = original.clone();

    assertEquals(original.getNodePositions().keySet(), cloned.getNodePositions().keySet());
    assertEquals(original.getNodePositions().get("scriptNode").getX(), cloned.getNodePositions().get("scriptNode").getX());
    assertEquals(original.getNodePositions().get("scriptNode").getY(), cloned.getNodePositions().get("scriptNode").getY());
    assertTrue(original.getNodePositions() != cloned.getNodePositions());
    assertTrue(original.getNodePositions().get("scriptNode") != cloned.getNodePositions().get("scriptNode"));
  }

  private static FlowModelValidationDiagnostic diagnostic(String diagnosticKind, String message, String... nodeNames) {
    return FlowModelValidationDiagnostic.builder()
        .diagnosticKind(diagnosticKind)
        .message(message)
        .nodeNames(Arrays.asList(nodeNames))
        .build();
  }
}
