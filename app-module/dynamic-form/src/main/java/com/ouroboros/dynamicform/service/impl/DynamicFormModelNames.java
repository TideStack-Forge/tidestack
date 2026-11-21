package com.ouroboros.dynamicform.service.impl;

import com.ouroboros.util.Strings;

final class DynamicFormModelNames {

  private DynamicFormModelNames() {
  }

  static String modelName(String formName) {
    return "DynamicForm" + Strings.upperFirst(sanitize(formName));
  }

  static String rawTableName(String formName) {
    return "t_dynamic_form_" + sanitize(Strings.toSnakeCase(formName));
  }

  private static String sanitize(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("[^A-Za-z0-9_]", "_")
        .replaceAll("_+", "_")
        .replaceAll("^_+|_+$", "");
  }
}
