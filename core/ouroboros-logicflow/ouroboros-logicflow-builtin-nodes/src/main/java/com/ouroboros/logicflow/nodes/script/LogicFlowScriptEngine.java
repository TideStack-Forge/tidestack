package com.ouroboros.logicflow.nodes.script;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.vavr.control.Try;

public interface LogicFlowScriptEngine {
  List<String> getSupportedLanguages();

  Function<Map<String, Object>, Try<Object>> compile(String language, String script);
}
