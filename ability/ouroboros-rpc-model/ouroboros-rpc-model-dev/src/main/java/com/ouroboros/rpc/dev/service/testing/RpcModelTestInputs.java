package com.ouroboros.rpc.dev.service.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ouroboros.rpc.util.ExpressionUtils;
import com.ouroboros.rpc.RpcFieldSemantics;
import com.ouroboros.rpc.util.RpcConfigurationTemplates;
import com.ouroboros.util.Json;

/** Discovers inputs without evaluating expressions or contacting authentication services. */
final class RpcModelTestInputs {
  record Variable(String name, List<String> references, boolean required, String suggestedType, boolean sensitive) { }
  record ConfigItem(String name, List<String> references, boolean hasDefault, String defaultSource, boolean sensitive) { }
  private final Map<String, List<String>> variables = new LinkedHashMap<>();
  private final Map<String, List<String>> configuration = new LinkedHashMap<>();
  private final Set<String> sensitiveVariables = new java.util.HashSet<>();
  private final Set<String> objectVariables = new java.util.HashSet<>();
  private final Set<String> requiredVariables = new java.util.HashSet<>();

  static RpcModelTestInputs discover(RpcModelTestSnapshot snapshot) {
    var inputs = new RpcModelTestInputs();
    snapshot.modelContents().keySet().stream().sorted().forEach(name -> {
      var model = snapshot.model(name);
      inputs.scan(Json.toMap(Json.toJsonString(model)), "rpc-model", null, "rpc-model:" + name, "", false, false);
    });
    snapshot.credentialContents().keySet().stream().sorted().forEach(name -> {
      var metadata = snapshot.credential(name);
      inputs.scan(metadata.getExtraProps(), "rpc-auth", metadata.getType(), "rpc-auth:" + name, "", true, false);
    });
    return inputs;
  }

  private void scan(Object value, String resource, String type, String owner, String pointer, boolean sensitive, boolean required) {
    String field = owner + pointer;
    if (value instanceof Map<?, ?> map) {
      boolean parameterRequired = Boolean.TRUE.equals(map.get("required"));
      for (var entry : map.entrySet()) {
        String key = String.valueOf(entry.getKey());
        scan(entry.getValue(), resource, type, owner, pointer + "/" + key.replace("~", "~0").replace("/", "~1"),
            sensitive, parameterRequired && "value".equals(key));
      }
    } else if (value instanceof List<?> list) {
      for (int index = 0; index < list.size(); index++) {
        scan(list.get(index), resource, type, owner, pointer + "/" + index, sensitive, required);
      }
    } else if (value instanceof String template) {
      var language = RpcFieldSemantics.language(resource, type, pointer);
      if (language == RpcFieldSemantics.Language.NONE) return;
      if (language != RpcFieldSemantics.Language.TEMPLATE) {
        try {
          RpcConfigurationTemplates.keys(template).forEach(name ->
              configuration.computeIfAbsent(name, ignore -> new ArrayList<>()).add(field));
        } catch (IllegalArgumentException error) {
          throw new RpcModelTestFailure("INPUT", "INPUT_INVALID", "配置引用无效", owner, field);
        }
        return;
      }
      var analysis = ExpressionUtils.analyze(template)
          .getOrElseThrow(error -> new RpcModelTestFailure("EXPRESSION", "EVALUATION_FAILED",
              "表达式无法分析，请检查模型字段", owner, field));
      analysis.configKeys().forEach(name -> configuration.computeIfAbsent(name, ignore -> new ArrayList<>()).add(field));
      for (var reference : analysis.variables()) {
        if (Set.of("accessToken", "timestamp", "nonce").contains(reference.root()) && sensitive) { continue; }
        variables.computeIfAbsent(reference.root(), ignore -> new ArrayList<>()).add(field);
        if (reference.dynamic() || reference.path().size() > 1) { objectVariables.add(reference.root()); }
        if (sensitive) { sensitiveVariables.add(reference.root()); }
        // Only a required whole selector proves a root mandatory; conditions are evaluated at execution.
        if (required && ExpressionUtils.segments(template).size() == 1
            && com.ouroboros.expression.EL.compile(template.startsWith("$") && !template.startsWith("${")
                ? "${" + template.substring(1) + "}" : template).map(plan -> plan.selectorPath().isPresent()).getOrElse(false)) {
          requiredVariables.add(reference.root());
        }
      }
    }
  }

  List<Variable> variables() {
    return variables.entrySet().stream().map(entry -> new Variable(entry.getKey(), List.copyOf(entry.getValue()),
        requiredVariables.contains(entry.getKey()), objectVariables.contains(entry.getKey()) ? "object" : "string",
        sensitiveVariables.contains(entry.getKey()))).toList();
  }

  List<ConfigItem> configItems(java.util.function.Function<String, String> source) {
    return configuration.entrySet().stream().map(entry -> {
      var origin = source.apply(entry.getKey());
      return new ConfigItem(entry.getKey(), List.copyOf(entry.getValue()), origin != null, origin, true);
    }).toList();
  }

  Set<String> configKeys() { return configuration.keySet(); }

  void validate(RpcModelTestRequest request) {
    if (!variables.keySet().containsAll(request.variables().keySet())
        || !configuration.keySet().containsAll(request.configValues().keySet())) {
      throw new RpcModelTestFailure("INPUT", "INPUT_INVALID", "测试参数包含未发现的变量或配置项", null, null);
    }
    for (var name : requiredVariables) {
      if (!request.variables().containsKey(name)) {
        throw new RpcModelTestFailure("INPUT", "INPUT_INVALID", "缺少必需变量", null, name);
      }
    }
  }
}
