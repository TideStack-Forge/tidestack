package com.ouroboros.webapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import com.ouroboros.expression.EL;
import com.ouroboros.expression.ExpressionPlan;

/** The declared HTTP source contract, shared by publication and route compilation. */
public final class BusinessActionWebApiContract {
  private BusinessActionWebApiContract() { }

  public static void validate(WebAPIModel model) {
    if (model.getExtraProp("businessActionName").isPresent()
        || model.getExtraProp("businessActionModelFullName").isPresent()) {
      throw new IllegalArgumentException("Legacy Action WebAPI metadata requires migration to businessModel/action");
    }
    if (model.getHandlerType() != HandlerType.BUSINESS_ACTION) {
      if (model.getExtraProp("businessModel").isPresent() || model.getExtraProp("action").isPresent()
          || model.getExtraProp("actionInputMapping").isPresent()) {
        throw new IllegalArgumentException("Action fields require handlerType=BusinessAction");
      }
      return;
    }
    if (model.getExtraProp("logicFlow").isPresent()
        || model.getExtraProp(String.class, "businessModel").filter(value -> !value.isBlank()).isEmpty()
        || model.getExtraProp(String.class, "action").filter(value -> !value.isBlank()).isEmpty()) {
      throw new IllegalArgumentException("BusinessAction routes require businessModel/action and cannot declare logicFlow");
    }
    compileInput(model);
  }

  public static void validateMapping(Map<?, ?> mapping, String path) {
    compileMapping(mapping, path, null);
  }

  public static void validateMapping(Map<?, ?> mapping, String path, String formatVersion) {
    compileMapping(mapping, path, formatVersion);
  }

  public static Map<String, ExpressionPlan> compileInput(WebAPIModel model) {
    Object version = model.getExtraProps().get("formatVersion");
    if (model.getExtraProps().containsKey("formatVersion") && !"2.0".equals(version)) {
      throw new IllegalArgumentException("BusinessAction WebAPI formatVersion must be absent or 2.0");
    }
    return compileMapping(model.getExtraProp(Map.class, "actionInputMapping").orElseThrow(() ->
        new IllegalArgumentException("BusinessAction route requires actionInputMapping (empty for no input)")),
        model.getPath(), version instanceof String text ? text : null);
  }

  public static Map<String, ExpressionPlan> compileMapping(Map<?, ?> mapping, String path, String formatVersion) {
    if (formatVersion != null && !"2.0".equals(formatVersion)) {
      throw new IllegalArgumentException("BusinessAction HTTP mapping formatVersion must be absent or 2.0");
    }
    List<String> names = new ArrayList<>();
    Map<String, ExpressionPlan> plans = new LinkedHashMap<>();
    mapping.forEach((key, value) -> {
      if (!(key instanceof String name) || !(value instanceof String source)
          || !name.matches("(params|data)(\\.[A-Za-z_$][A-Za-z0-9_$]*(\\[\\])?)*")) {
        throw new IllegalArgumentException("Invalid Action HTTP input mapping");
      }
      if (name.endsWith("[]")) {
        throw new IllegalArgumentException("Array item mapping requires an item property");
      }
      String expression = formatVersion == null ? legacyExpression(source) : source;
      if (formatVersion != null && source.startsWith("$.")) {
        throw new IllegalArgumentException("BusinessAction HTTP 2.0 mappings use the platform EL syntax");
      }
      ExpressionPlan plan = EL.compile(expression).getOrElseThrow(error ->
          new IllegalArgumentException("Invalid Action HTTP input mapping: " + name, error));
      plan.selectorPath().filter(selector -> selector.size() >= 3 && "request".equals(selector.get(0))
          && "pathVariables".equals(selector.get(1))).ifPresent(selector -> {
            if (path == null || !path.matches(".*\\{" + java.util.regex.Pattern.quote(String.valueOf(selector.get(2))) + "(?::[^}]+)?}.*")) {
              throw new IllegalArgumentException("Action HTTP input mapping references an unknown path variable");
            }
          });
      plans.put(name, plan);
      names.add(name);
    });
    for (String name : names) {
      for (String other : names) {
        if (!name.equals(other) && overlapping(name, other)) {
          throw new IllegalArgumentException("Action HTTP input mapping has overlapping destinations");
        }
      }
    }
    return java.util.Collections.unmodifiableMap(plans);
  }

  /** Reuses the owning HTTP contract's conversion for an explicit metadata upgrade. */
  public static Map<String, String> upgradeMapping(Map<?, ?> mapping, String path) {
    compileMapping(mapping, path, null);
    Map<String, String> upgraded = new LinkedHashMap<>();
    mapping.forEach((key, value) -> upgraded.put((String) key, legacyExpression((String) value)));
    return java.util.Collections.unmodifiableMap(upgraded);
  }

  private static String legacyExpression(String source) {
    if (!source.matches("\\$\\.(query|pathVariables|body|headers)(\\.[A-Za-z0-9_$-]+)*")) {
      throw new IllegalArgumentException("Invalid legacy Action HTTP input mapping");
    }
    String[] segments = source.substring(2).split("\\.");
    StringBuilder expression = new StringBuilder("${ ");
    if ("body".equals(segments[0]) || "pathVariables".equals(segments[0])) {
      expression.append("request['").append(segments[0]).append("']");
    } else {
      expression.append(segments[0]);
    }
    for (int index = 1; index < segments.length; index++) {
      expression.append("['").append(segments[index]).append("']");
    }
    return expression.append(" }").toString();
  }

  private static boolean overlapping(String first, String second) {
    String[] left = first.split("\\.");
    String[] right = second.split("\\.");
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      if (!left[index].replace("[]", "").equals(right[index].replace("[]", ""))) { return false; }
      if (!left[index].equals(right[index]) || left[index].endsWith("[]")) { return true; }
    }
    return true;
  }

}
