package com.ouroboros.logicflow.nodes.support;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import io.vavr.control.Try;

import com.ouroboros.logicflow.nodes.script.LogicFlowScriptEngine;
import com.ouroboros.logicflow.support.LogicFlowServices;

public final class LogicFlowScripts {
  private static final String MISSING_ENGINE_MESSAGE = "No LogicFlowScriptEngine registered for language: ";

  private LogicFlowScripts() {
  }

  public static Function<Map<String, Object>, Try<Object>> compile(String language, String script) {
    return LogicFlowServices.cachedReversedServiceStream(LogicFlowScriptEngine.class)
        .filter(engine -> engine.getSupportedLanguages().contains(language))
        .findFirst()
        .map(engine -> engine.compile(language, script))
        .orElseGet(() -> missingEngineRunner(language));
  }

  public static List<String> getSupportedLanguages() {
    return LogicFlowServices.cachedReversedServiceStream(LogicFlowScriptEngine.class)
        .flatMap(engine -> engine.getSupportedLanguages().stream())
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  private static Function<Map<String, Object>, Try<Object>> missingEngineRunner(String language) {
    return bindings -> Try.failure(new IllegalStateException(MISSING_ENGINE_MESSAGE + language));
  }
}
