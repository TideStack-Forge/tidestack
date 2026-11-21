package com.ouroboros.logicflow.manage.service;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.logicflow.manage.service.RuntimeFlowManagement.Difference;
import com.ouroboros.logicflow.util.LogicFlowJson;

/** Paths and change kinds are safe summaries: model parameter values stay in the protected views. */
final class FlowModelDifferences {
  private FlowModelDifferences() { }

  static List<Difference> between(String before, String after) {
    try {
      var values = new ArrayList<Difference>();
      compare(before == null ? null : LogicFlowJson.OBJECT_MAPPER.readTree(before),
          after == null ? null : LogicFlowJson.OBJECT_MAPPER.readTree(after), "", values, 0);
      return List.copyOf(values);
    } catch (java.io.IOException failure) {
      throw new IllegalArgumentException("无法比较逻辑流正式定义", failure);
    }
  }

  private static void compare(JsonNode before, JsonNode after, String path, List<Difference> values, int depth) {
    if (java.util.Objects.equals(before, after) || values.size() >= 500) { return; }
    if (before == null || after == null || depth > 64) {
      values.add(new Difference(path, before == null ? "added" : after == null ? "removed" : "changed"));
    } else if (before.isObject() && after.isObject()) {
      var fields = new TreeSet<String>();
      before.fieldNames().forEachRemaining(fields::add);
      after.fieldNames().forEachRemaining(fields::add);
      fields.forEach(field -> compare(before.get(field), after.get(field),
          path + "/" + field.replace("~", "~0").replace("/", "~1"), values, depth + 1));
    } else if (before.isArray() && after.isArray()) {
      for (int index = 0; index < Math.max(before.size(), after.size()); index++) {
        compare(before.get(index), after.get(index), path + "/" + index, values, depth + 1);
      }
    } else {
      values.add(new Difference(path, "changed"));
    }
  }
}
