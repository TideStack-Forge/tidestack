package com.ouroboros.logicflow.script;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Stream;

import io.vavr.control.Try;
import jakarta.annotation.Priority;

import com.ouroboros.logicflow.nodes.script.LogicFlowScriptEngine;
import com.ouroboros.script.ScriptCompiler;
import com.ouroboros.script.Scripts;

@Priority(-1000)
public final class CommonScriptLogicFlowScriptEngine implements LogicFlowScriptEngine {
  private static final List<String> BUILTIN_LANGUAGES = List.of("js", "javascript", "groovy");

  @Override
  public List<String> getSupportedLanguages() {
    return Stream.concat(BUILTIN_LANGUAGES.stream(), serviceCompilerLanguages())
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  @Override
  public Function<Map<String, Object>, Try<Object>> compile(String language, String script) {
    return Scripts.compile(language, script);
  }

  private Stream<String> serviceCompilerLanguages() {
    return ServiceLoader.load(ScriptCompiler.class).stream()
        .map(ServiceLoader.Provider::get)
        .map(ScriptCompiler::getLanguage);
  }
}
