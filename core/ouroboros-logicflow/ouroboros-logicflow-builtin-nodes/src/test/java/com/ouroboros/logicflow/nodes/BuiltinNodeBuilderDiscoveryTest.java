package com.ouroboros.logicflow.nodes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ouroboros.logicflow.model.DefaultFlowModel;
import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.support.LogicFlowScripts;

public class BuiltinNodeBuilderDiscoveryTest {
  @Test
  public void scriptNodeShouldBeDiscoveredThroughNodeBuilderDescriptor() {
    var flowModel = FlowModelFactory.build(scriptFlow(FlowNodeMeta.builder()
        .name("script")
        .type("script")
        .inputPort("input", "string")
        .build()), NodeReferenceCatalog.empty());
    var result = flowModel.execute("hello");

    assertTrue("ScriptNode should be available through builtin NodeBuilder SPI", flowModel.isValid());
    assertTrue("ScriptNode should execute after SPI discovery", result.isSuccess());
    assertEquals("hello", result.get());
  }

  @Test
  public void scriptNodeShouldRunThroughRegisteredScriptEngine() {
    var flowModel = FlowModelFactory.build(scriptFlow(FlowNodeMeta.builder()
        .name("script")
        .type("script")
        .inputPort("input", "string")
        .extraProp("script", "return $0 + '-engine'")
        .build()), NodeReferenceCatalog.empty());

    var result = flowModel.execute("hello");

    assertTrue("ScriptNode should run through registered LogicFlowScriptEngine", result.isSuccess());
    assertEquals("hello-engine", result.get());
  }

  @Test
  public void scriptNodeShouldForwardLanguageKeyToRegisteredScriptEngine() {
    var flowModel = FlowModelFactory.build(scriptFlow(FlowNodeMeta.builder()
        .name("script")
        .type("script")
        .inputPort("input", "string")
        .extraProp("language", "tenant-expression-v1")
        .extraProp("script", "return language")
        .build()), NodeReferenceCatalog.empty());

    var result = flowModel.execute("hello");

    assertTrue("ScriptNode should not restrict script language to builtin enum values", result.isSuccess());
    assertEquals("tenant-expression-v1", result.get());
  }

  @Test
  public void scriptEnginesShouldExposeSupportedLanguageList() {
    var supportedLanguages = LogicFlowScripts.getSupportedLanguages();

    assertTrue(supportedLanguages.contains("groovy"));
    assertTrue(supportedLanguages.contains("tenant-expression-v1"));
  }

  @Test
  public void scriptNodeShouldFailWhenNoRegisteredScriptEngineDeclaresLanguage() {
    var flowModel = FlowModelFactory.build(scriptFlow(FlowNodeMeta.builder()
        .name("script")
        .type("script")
        .inputPort("input", "string")
        .extraProp("language", "missing-script-engine")
        .extraProp("script", "return language")
        .build()), NodeReferenceCatalog.empty());

    var result = flowModel.execute("hello");

    assertTrue("ScriptNode should fail when no LogicFlowScriptEngine declares the language", result.isFailure());
    assertTrue(hasCauseMessage(result.getCause(), "missing-script-engine"));
  }

  private FlowModelMeta scriptFlow(FlowNodeMeta scriptNodeMeta) {
    return FlowModelMeta.builder()
        .name("script-discovery")
        .inputDataType("string")
        .outputDataType("string")
        .node(scriptNodeMeta)
        .entryNode("script")
        .exitNode("script")
        .build();
  }

  private boolean hasCauseMessage(Throwable throwable, String expectedMessagePart) {
    var current = throwable;
    while (current != null) {
      if (current.getMessage() != null && current.getMessage().contains(expectedMessagePart)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
