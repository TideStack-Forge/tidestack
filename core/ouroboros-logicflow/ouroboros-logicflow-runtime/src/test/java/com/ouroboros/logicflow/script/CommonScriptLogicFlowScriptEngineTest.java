package com.ouroboros.logicflow.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CommonScriptLogicFlowScriptEngineTest {
  private final CommonScriptLogicFlowScriptEngine engine = new CommonScriptLogicFlowScriptEngine();

  @Test
  void delegatesGroovyCompilationToCommonScript() {
    assertTrue(engine.getSupportedLanguages().contains("groovy"));

    var runner = engine.compile("groovy", "return $0 + '-platform'");

    var result = runner.apply(Map.of("$0", "logicflow"));

    assertTrue(result.isSuccess());
    assertEquals("logicflow-platform", result.get());
  }

  @Test
  void exposesCommonScriptSupportedLanguages() {
    assertTrue(engine.getSupportedLanguages().containsAll(List.of("groovy", "js", "javascript")));
    assertFalse(engine.getSupportedLanguages().contains("unknown"));
  }

  @Test
  void keepsCommonScriptUnsupportedLanguageFailure() {
    var runner = engine.compile("unknown", "return $0");

    var result = runner.apply(Map.of("$0", "logicflow"));

    assertTrue(result.isFailure());
  }
}
