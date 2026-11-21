package com.ouroboros.logicflow.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.vavr.control.Try;

import com.ouroboros.logicflow.nodes.script.LogicFlowScriptEngine;

public final class TestLogicFlowScriptEngine implements LogicFlowScriptEngine {
  @Override
  public List<String> getSupportedLanguages() {
    return List.of("groovy", "tenant-expression-v1");
  }

  @Override
  public Function<Map<String, Object>, Try<Object>> compile(String language, String script) {
    return bindings -> Try.of(() -> run(language, script, bindings));
  }

  private Object run(String language, String script, Map<String, Object> bindings) {
    if ("throw new RuntimeException('boom')".equals(script)) {
      throw new RuntimeException("boom");
    }
    if ("throw new RuntimeException('catch-boom:' + $0.nodeName)".equals(script)) {
      throw new RuntimeException("catch-boom:" + getInputProperty(bindings, "nodeName"));
    }
    if ("throw new com.ouroboros.logicflow.support.TestErrorCodeException(4321, 'biz-boom')".equals(script)) {
      throw new TestErrorCodeException(4321, "biz-boom");
    }
    if ("return $0.nodeName".equals(script)) {
      return getInputProperty(bindings, "nodeName");
    }
    if ("return $0 + '-engine'".equals(script)) {
      return bindings.get("$0") + "-engine";
    }
    if ("return language".equals(script)) {
      return language;
    }
    if ("return 'recovered'".equals(script)) {
      return "recovered";
    }
    if (script != null && script.startsWith("return [exceptionType:")) {
      return errorContext(bindings);
    }
    throw new IllegalArgumentException("Unsupported test script: " + script);
  }

  private Object getInputProperty(Map<String, Object> bindings, String propertyName) {
    if (bindings == null || !(bindings.get("$0") instanceof Map<?, ?> input)) {
      return null;
    }
    return input.get(propertyName);
  }

  private Map<String, Object> errorContext(Map<String, Object> bindings) {
    var result = new LinkedHashMap<String, Object>();
    if (bindings == null || !(bindings.get("$0") instanceof Map<?, ?> input)) {
      return result;
    }
    result.put("exceptionType", input.get("exceptionType"));
    result.put("exceptionMessage", input.get("exceptionMessage"));
    result.put("exceptionCode", input.get("exceptionCode"));
    result.put("flowName", input.get("flowName"));
    result.put("nodeName", input.get("nodeName"));
    result.put("nodeInputs", input.get("nodeInputs"));
    result.put("flowInputs", input.get("flowInputs"));
    return result;
  }
}
