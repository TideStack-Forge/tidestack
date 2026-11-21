package com.ouroboros.workflow.approval.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.CandidateProvider;

final class RuntimeCandidateSchemaFactory {
  private RuntimeCandidateSchemaFactory() {
  }

  static SchemaNode buildEnumSelector(String valueKey, String label, List<CandidateProvider.CandidateItem> options) {
    SchemaNode schema = buildSelectionBase("list-select", valueKey, "请选择" + label);
    schema.setProp("options", options);
    schema.setProp("clearable", true);
    return schema;
  }

  static SchemaNode buildHiddenChecked(String valueKey) {
    SchemaNode schema = new SchemaNode();
    schema.setType("hidden");
    schema.setName(valueKey);
    schema.setProp("value", true);
    return schema;
  }

  static SchemaNode buildMultiSelect(String valueKey, String label, Integer min, Integer max) {
    SchemaNode schema = buildSelectionBase("select", valueKey, buildMultiSelectLabel(label, min, max));
    schema.setProp("searchable", true);
    schema.setProp("multiple", true);
    Map<String, Object> validations = buildLengthValidations(min, max);
    if (!validations.isEmpty()) {
      schema.setProp("validations", validations);
    }
    Map<String, String> validationErrors = buildLengthValidationErrors(label, min, max);
    if (!validationErrors.isEmpty()) {
      schema.setProp("validationErrors", validationErrors);
    }
    return schema;
  }

  static SchemaNode buildDynamicUserPicker(String valueKey, String label, Integer min, Integer max) {
    SchemaNode schema = new SchemaNode();
    schema.setType("dynamic-tree-transfer-picker");
    schema.setName(valueKey);
    schema.setProp("label", "请选择" + label);
    schema.setProp("mode", "normal");
    schema.setProp("required", true);
    schema.setProp("validateOnChange", true);
    schema.setProp("pickerSize", "lg");
    Map<String, Object> validations = buildLengthValidations(min, max);
    if (!validations.isEmpty()) {
      schema.setProp("validations", validations);
    }
    Map<String, String> validationErrors = buildLengthValidationErrors(label, min, max);
    if (!validationErrors.isEmpty()) {
      schema.setProp("validationErrors", validationErrors);
    }
    return schema;
  }

  static SchemaNode buildFormFieldSelector(String valueKey) {
    SchemaNode schema = buildSelectionBase("select", valueKey, "请选择处理人字段");
    schema.setProp("source", "$candidateFieldSource.fields");
    schema.setProp("labelField", "label");
    schema.setProp("valueField", "value");
    schema.setProp("searchable", true);
    schema.setProp("clearable", true);
    return schema;
  }

  private static SchemaNode buildSelectionBase(String type, String valueKey, String label) {
    SchemaNode schema = new SchemaNode();
    schema.setType(type);
    schema.setName(valueKey);
    schema.setProp("label", label);
    schema.setProp("joinValues", false);
    schema.setProp("extractValue", true);
    schema.setProp("required", true);
    schema.setProp("mode", "normal");
    schema.setProp("validateOnChange", true);
    return schema;
  }

  private static String buildMultiSelectLabel(String label, Integer min, Integer max) {
    int normalizedMin = min == null ? 0 : min;
    String normalizedMax = max == null ? "无限" : String.valueOf(max);
    return "请选择" + label + " ( " + normalizedMin + " - " + normalizedMax + " )";
  }

  private static Map<String, Object> buildLengthValidations(Integer min, Integer max) {
    Map<String, Object> validations = new LinkedHashMap<>();
    if (min != null) {
      validations.put("minLength", min);
    }
    if (max != null) {
      validations.put("maxLength", max);
    }
    return validations;
  }

  private static Map<String, String> buildLengthValidationErrors(String label, Integer min, Integer max) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (min != null) {
      errors.put("minLength", label + "至少选择" + min + "个");
    }
    if (max != null) {
      errors.put("maxLength", label + "最多只能选择" + max + "个");
    }
    return errors;
  }
}
