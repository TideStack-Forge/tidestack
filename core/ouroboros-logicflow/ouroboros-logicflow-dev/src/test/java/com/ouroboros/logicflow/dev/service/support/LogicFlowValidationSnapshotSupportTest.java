package com.ouroboros.logicflow.dev.service.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowModelValidationDiagnostic;
import com.ouroboros.logicflow.model.FlowModelValidationState;
import com.ouroboros.logicflow.model.FlowNodePortMeta;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;

public class LogicFlowValidationSnapshotSupportTest {
  @Test
  public void validModelShouldRemainEnabledAndClearValidationSnapshot() {
    var original = FlowModelMeta.builder()
        .name("valid-flow")
        .entryNode("scriptNode")
        .enabled(true)
        .validationSummary("旧错误")
        .validationErrors(Arrays.asList("旧错误1", "旧错误2"))
        .node(scriptNode("scriptNode"))
        .node(returnNode("returnNode"))
        .connect("scriptNode", "returnNode")
        .build();

    var normalized = normalize(original);

    assertNotSame(original, normalized);
    assertTrue(normalized.isEnabled());
    assertEquals(FlowModelValidationState.VALID, normalized.getValidationState());
    assertNull(normalized.getValidationSummary());
    assertTrue(normalized.getValidationErrors().isEmpty());
    assertTrue(normalized.getValidationDiagnostics().isEmpty());
    assertNotNull(normalized.getLastValidatedTime());
  }

  @Test
  public void invalidEnabledModelShouldRemainEnabledAndPersistOrderedValidationErrors() {
    var original = FlowModelMeta.builder()
        .name("invalid-flow")
        .entryNode("scriptNode")
        .enabled(true)
        .node(scriptNode("scriptNode"))
        .build();

    var normalized = normalize(original);

    assertNotSame(original, normalized);
    assertTrue(normalized.isEnabled());
    assertEquals(FlowModelValidationState.INVALID, normalized.getValidationState());
    assertNotNull(normalized.getLastValidatedTime());
    assertFalse(normalized.getValidationErrors().isEmpty());
    assertFalse(normalized.getValidationDiagnostics().isEmpty());
    assertEquals(normalized.getValidationErrors().get(0), normalized.getValidationSummary());
    assertEquals(normalized.getValidationErrors(), normalized.getValidationDiagnostics().stream()
        .map(FlowModelValidationDiagnostic::getMessage)
        .collect(Collectors.toList()));
    assertTrue(normalized.getValidationErrors().stream()
        .anyMatch(message -> message.contains("成功终点")));
    assertEquals("missing_successful_terminal",
        findDiagnostic(normalized, "missing_successful_terminal").getDiagnosticKind());
    assertTrue(findDiagnostic(normalized, "missing_successful_terminal").getNodeNames().isEmpty());
    assertEquals(Arrays.asList("scriptNode"), findDiagnostic(normalized, "invalid_leaf_nodes").getNodeNames());
  }

  @Test
  public void invalidDisabledModelShouldRemainDisabledAndPersistOrderedValidationErrors() {
    var original = FlowModelMeta.builder()
        .name("invalid-disabled-flow")
        .entryNode("scriptNode")
        .enabled(false)
        .node(scriptNode("scriptNode"))
        .build();

    var normalized = normalize(original);

    assertNotSame(original, normalized);
    assertFalse(normalized.isEnabled());
    assertEquals(FlowModelValidationState.INVALID, normalized.getValidationState());
    assertNotNull(normalized.getLastValidatedTime());
    assertFalse(normalized.getValidationErrors().isEmpty());
    assertEquals(normalized.getValidationErrors().get(0), normalized.getValidationSummary());
    assertEquals(normalized.getValidationErrors(), normalized.getValidationDiagnostics().stream()
        .map(FlowModelValidationDiagnostic::getMessage)
        .collect(Collectors.toList()));
  }

  @Test
  public void directNodeDesignErrorsShouldMapToNavigableDiagnostics() {
    var original = FlowModelMeta.builder()
        .name("unknown-type-flow")
        .entryNode("badNode")
        .enabled(true)
        .node(FlowNodeMeta.builder()
            .name("badNode")
            .type("unknown-type")
            .build())
        .build();

    var normalized = normalize(original);

    var diagnostic = findDiagnostic(normalized, "node_design_error");
    assertEquals("未知的节点类型", diagnostic.getMessage());
    assertEquals(Arrays.asList("badNode"), diagnostic.getNodeNames());
    assertEquals("node", diagnostic.getScope());
  }

  @Test
  public void missingEntryNodeShouldExposeFlowFieldPath() {
    var original = FlowModelMeta.builder()
        .name("missing-entry-flow")
        .enabled(true)
        .node(returnNode("returnNode"))
        .build();

    var normalized = normalize(original);

    var diagnostic = findDiagnostic(normalized, "entry_node_not_found");
    assertEquals("flow", diagnostic.getScope());
    assertEquals("entryNode", diagnostic.getFieldPath());
    assertTrue(diagnostic.getNodeNames().isEmpty());
  }

  @Test
  public void callFlowNodeErrorsShouldExposeFlowNameFieldPath() {
    var original = FlowModelMeta.builder()
        .name("call-flow-field-path")
        .entryNode("caller")
        .enabled(true)
        .node(FlowNodeMeta.builder()
            .name("caller")
            .type("callFlow")
            .build())
        .build();

    var normalized = normalize(original);

    var diagnostic = findDiagnostic(normalized, "node_design_error");
    assertEquals("node", diagnostic.getScope());
    assertEquals(Arrays.asList("caller"), diagnostic.getNodeNames());
    assertEquals("flowName", diagnostic.getFieldPath());
  }

  @Test
  public void outputPortMetadataErrorsShouldExposePortFieldPath() {
    var original = FlowModelMeta.builder()
        .name("output-port-field-path")
        .entryNode("entry")
        .exitNode("exit")
        .enabled(true)
        .node(FlowNodeMeta.builder()
            .name("entry")
            .type("script")
            .outputPort(FlowNodePortMeta.builder()
                .name("branch")
                .defaultBranch(true)
                .build())
            .build())
        .node(returnNode("exit"))
        .connect("entry", "exit")
        .build();

    var normalized = normalize(original);

    var diagnostic = findDiagnostic(normalized, "node_design_error");
    assertEquals("node", diagnostic.getScope());
    assertEquals(Arrays.asList("entry"), diagnostic.getNodeNames());
    assertEquals("outputPorts[0].exclusiveGroupId", diagnostic.getFieldPath());
    assertEquals("output", diagnostic.getPortKind());
    assertEquals(Integer.valueOf(0), diagnostic.getPortIndex());
  }

  @Test
  public void invalidConnectionShouldExposeEdgeEndpointIdentity() {
    var original = FlowModelMeta.builder()
        .name("invalid-edge-field-path")
        .entryNode("source")
        .enabled(true)
        .node(scriptNode("source"))
        .node(returnNode("target"))
        .connect("source", 0, "target", 2)
        .build();

    var normalized = normalize(original);

    var diagnostic = findDiagnostic(normalized, "invalid_connection");
    assertEquals("edge", diagnostic.getScope());
    assertEquals("targetInputPort", diagnostic.getFieldPath());
    assertEquals("source", diagnostic.getSourceNode());
    assertEquals(Integer.valueOf(0), diagnostic.getSourceOutputPort());
    assertEquals("target", diagnostic.getTargetNode());
    assertEquals(Integer.valueOf(2), diagnostic.getTargetInputPort());
  }

  @Test
  public void problematicNodesShouldFlattenIntoPerNodeDiagnostics() {
    var original = FlowModelMeta.builder()
        .name("problematic-flow")
        .entryNode("switchNode")
        .enabled(true)
        .node(switchNodeWithInvalidCondition("switchNode"))
        .node(returnNode("returnNode"))
        .connect("switchNode", 1, "returnNode", 0)
        .build();

    var normalized = normalize(original);

    var diagnostic = findDiagnostic(normalized, "node_design_error");
    assertEquals("条件表达式错误", diagnostic.getMessage());
    assertEquals(Arrays.asList("switchNode"), diagnostic.getNodeNames());
  }

  @Test
  public void unreachableNodesShouldExposeOrderedNodeNamesInDiagnostics() {
    var original = FlowModelMeta.builder()
        .name("unreachable-flow")
        .entryNode("entryNode")
        .enabled(true)
        .node(scriptNode("entryNode"))
        .node(returnNode("returnNode"))
        .node(scriptNode("orphan-a"))
        .node(scriptNode("orphan-b"))
        .connect("entryNode", "returnNode")
        .build();

    var normalized = normalize(original);

    var diagnostic = findDiagnostic(normalized, "unreachable_nodes");
    assertEquals(Arrays.asList("orphan-a", "orphan-b"), diagnostic.getNodeNames());
  }

  @Test
  public void invalidLeafNodesShouldExposeOrderedNodeNamesInDiagnostics() {
    var original = FlowModelMeta.builder()
        .name("invalid-leaf-flow")
        .entryNode("switchNode")
        .enabled(true)
        .node(validSwitchNode("switchNode"))
        .node(scriptNode("leaf-a"))
        .node(scriptNode("leaf-b"))
        .connect("switchNode", 0, "leaf-a", 0)
        .connect("switchNode", 1, "leaf-b", 0)
        .build();

    var normalized = normalize(original);

    var diagnostic = findDiagnostic(normalized, "invalid_leaf_nodes");
    assertEquals(Arrays.asList("leaf-a", "leaf-b"), diagnostic.getNodeNames());
  }

  @Test
  public void invalidFlowScopeShouldExposeNavigableNodeNamesInDiagnostics() {
    var original = FlowModelMeta.builder()
        .name("invalid-flow-scope")
        .entryNode("mainEntry")
        .exitNode("mainExit")
        .errorEntryNode("sharedNode")
        .enabled(true)
        .node(scriptNode("mainEntry"))
        .node(scriptNode("sharedNode"))
        .node(scriptNode("mainExit"))
        .node(returnNode("errorReturn"))
        .connect("mainEntry", "sharedNode")
        .connect("sharedNode", "mainExit")
        .connect("sharedNode", "errorReturn")
        .build();

    var normalized = normalize(original);

    var diagnostic = findDiagnostic(normalized, "invalid_flow_scope");
    assertEquals(Arrays.asList("sharedNode", "mainExit", "errorReturn"), diagnostic.getNodeNames());
  }

  private static FlowNodeMeta scriptNode(String name) {
    return FlowNodeMeta.builder()
        .name(name)
        .type("script")
        .build();
  }

  private static FlowModelMeta normalize(FlowModelMeta model) {
    return LogicFlowValidationSnapshotSupport.normalize(
        model,
        FlowModelFactory.build(model, NodeReferenceCatalog.empty())
    );
  }

  private static FlowNodeMeta returnNode(String name) {
    return FlowNodeMeta.builder()
        .name(name)
        .type("return")
        .build();
  }

  private static FlowNodeMeta switchNodeWithInvalidCondition(String name) {
    return FlowNodeMeta.builder()
        .name(name)
        .type("switch")
        .extraProp("conditions", Arrays.asList("bad-condition"))
        .build();
  }

  private static FlowNodeMeta validSwitchNode(String name) {
    return FlowNodeMeta.builder()
        .name(name)
        .type("switch")
        .extraProp("conditions", Arrays.asList(condition("first", "true")))
        .build();
  }

  private static HashMap<String, Object> condition(String name, String expression) {
    var condition = new HashMap<String, Object>();
    condition.put("name", name);
    condition.put("description", "");
    condition.put("expression", expression);
    return condition;
  }

  private static FlowModelValidationDiagnostic findDiagnostic(FlowModelMeta model, String diagnosticKind) {
    return model.getValidationDiagnostics().stream()
        .filter(diagnostic -> diagnosticKind.equals(diagnostic.getDiagnosticKind()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing diagnostic: " + diagnosticKind));
  }
}
