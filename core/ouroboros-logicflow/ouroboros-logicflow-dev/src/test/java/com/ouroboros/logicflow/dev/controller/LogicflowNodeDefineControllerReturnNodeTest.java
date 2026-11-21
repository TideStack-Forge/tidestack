package com.ouroboros.logicflow.dev.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Test;

import com.ouroboros.logicflow.authoring.model.NodeDefine;
import com.ouroboros.logicflow.authoring.provider.LogicFlowNodeDefineProvider;
import com.ouroboros.util.Json;

public class LogicflowNodeDefineControllerReturnNodeTest {
  @Test
  public void nodeDefineApiShouldExposeReturnNodeAuthoringContract() {
    var result = new LogicflowNodeDefineController().getNodeDefines();
    var nodeDefines = Json.toList(Json.toJsonString(result.getData()));

    var returnNode = nodeDefines.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .filter(node -> "return".equals(node.get("type")))
        .findFirst()
        .orElse(null);

    assertNotNull(returnNode);

    var editorCapabilities = (Map<String, Object>) returnNode.get("editorCapabilities");
    assertNotNull(editorCapabilities);
    assertTrue(Boolean.TRUE.equals(returnNode.get("isTerminal")));
    assertTrue("successful".equals(returnNode.get("terminalKind")));
    assertFalse(Boolean.TRUE.equals(editorCapabilities.get("canBeEntry")));
    assertFalse(Boolean.TRUE.equals(editorCapabilities.get("canBeExit")));
    assertFalse(Boolean.TRUE.equals(editorCapabilities.get("canBeErrorEntry")));
    assertTrue(Boolean.TRUE.equals(editorCapabilities.get("isSuccessfulTerminal")));
  }

  @Test
  public void nodeDefineApiShouldExposeExceptionTerminalKind() {
    var nodeDefine = Json.toBean(NodeDefine.class,
        "{\"type\":\"ouroborosExceptions\",\"isTerminal\":true,\"terminalKind\":\"exception\"}");

    assertNotNull(nodeDefine);
    assertTrue(Boolean.TRUE.equals(nodeDefine.getIsTerminal()));
    assertTrue("exception".equals(nodeDefine.getTerminalKind()));
  }

  @Test
  public void nodeDefineApiShouldMergeDuplicateNodeTypes() {
    var merged = LogicflowNodeDefineController.mergeNodeDefines(Stream.of(
        provider(new NodeDefine().setType("customNode").setLabel("旧定义").setOrder(20)),
        provider(new NodeDefine().setType("customNode").setLabel("新定义").setOrder(10))
    ));

    assertEquals(1, merged.size());
    assertEquals("新定义", merged.get(0).getLabel());
  }

  private LogicFlowNodeDefineProvider provider(NodeDefine... nodeDefines) {
    return () -> List.of(nodeDefines);
  }
}
