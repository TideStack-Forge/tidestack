package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;

public class DynamicFilterCompiler {
  public static final String CONTEXT_KEY = "__statisticsReportingCompiledDynamicFilter";
  private static final String FIELD_ALIAS = "__ouroboros_view__";
  private static final long MAX_OFFSET = Integer.MAX_VALUE;
  private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  public DatasetExecutionRequest compile(DatasetDefinition dataset, DatasetExecutionRequest request) {
    if (dataset == null) {
      throw new IllegalArgumentException("dataset definition is required");
    }
    if (request == null) {
      throw new IllegalArgumentException("dataset execution request is required");
    }
    validatePage(request.getPage(), request.getExecutionBudget());

    DynamicFilterSpec spec = request.getDynamicFilter();
    if ((spec == null || (spec.getFilter() == null && isEmpty(spec.getSort()))) && request.getPage() == null) {
      return request;
    }

    DatasetExecutionRequest compiledRequest = request.copy();
    ParameterNameSequence parameterSequence = new ParameterNameSequence();
    Map<String, String> allowedFields = collectAllowedFields(dataset.getSchema());
    CompiledDynamicFilter compiled = new CompiledDynamicFilter();
    if (spec != null && spec.getFilter() != null) {
      compiled.setWhereClause(compileFilter(spec.getFilter(), allowedFields, compiled.getParameters(), parameterSequence));
    }
    if (spec != null && !isEmpty(spec.getSort())) {
      compiled.setOrderByClause(compileSort(spec.getSort(), allowedFields));
    }

    Map<String, Object> executionContext = compiledRequest.getExecutionContext() == null
        ? new LinkedHashMap<String, Object>()
        : compiledRequest.getExecutionContext();
    executionContext.put(CONTEXT_KEY, compiled);
    compiledRequest.setExecutionContext(executionContext);
    return compiledRequest;
  }

  private Map<String, String> collectAllowedFields(DatasetSchemaDefinition schema) {
    Map<String, String> fields = new LinkedHashMap<String, String>();
    if (schema == null || schema.getFields() == null) {
      return fields;
    }
    for (DatasetFieldDefinition field : schema.getFields()) {
      if (field != null && !isBlank(field.getName())) {
        fields.put(field.getName().toLowerCase(Locale.ROOT), field.getName());
      }
    }
    return fields;
  }

  private String compileFilter(FilterNode node,
                               Map<String, String> allowedFields,
                               Map<String, Object> parameters,
                               ParameterNameSequence parameterSequence) {
    if (node == null || isBlank(node.getOp())) {
      throw new IllegalArgumentException("filter-operator-invalid");
    }
    String op = node.getOp().trim();
    if ("and".equalsIgnoreCase(op) || "or".equalsIgnoreCase(op)) {
      return compileLogical(node, op.toUpperCase(Locale.ROOT), allowedFields, parameters, parameterSequence);
    }
    return compileCondition(node, op, allowedFields, parameters, parameterSequence);
  }

  private String compileLogical(FilterNode node,
                                String op,
                                Map<String, String> allowedFields,
                                Map<String, Object> parameters,
                                ParameterNameSequence parameterSequence) {
    if (node.getChildren() == null || node.getChildren().isEmpty()) {
      throw new IllegalArgumentException("filter-operator-invalid");
    }
    List<String> children = new ArrayList<String>();
    for (FilterNode child : node.getChildren()) {
      if (child == null) {
        throw new IllegalArgumentException("filter-operator-invalid");
      }
      children.add("(" + compileFilter(child, allowedFields, parameters, parameterSequence) + ")");
    }
    return String.join(" " + op + " ", children);
  }

  private String compileCondition(FilterNode node,
                                  String op,
                                  Map<String, String> allowedFields,
                                  Map<String, Object> parameters,
                                  ParameterNameSequence parameterSequence) {
    String column = columnFor(node.getField(), allowedFields, "filter-field-not-allowed");
    if ("eq".equals(op)) {
      return column + " = " + bind(node.getValue(), parameters, parameterSequence);
    }
    if ("neq".equals(op)) {
      return column + " <> " + bind(node.getValue(), parameters, parameterSequence);
    }
    if ("gt".equals(op)) {
      return column + " > " + bind(node.getValue(), parameters, parameterSequence);
    }
    if ("gte".equals(op)) {
      return column + " >= " + bind(node.getValue(), parameters, parameterSequence);
    }
    if ("lt".equals(op)) {
      return column + " < " + bind(node.getValue(), parameters, parameterSequence);
    }
    if ("lte".equals(op)) {
      return column + " <= " + bind(node.getValue(), parameters, parameterSequence);
    }
    if ("like".equals(op)) {
      return column + " LIKE " + bind(node.getValue(), parameters, parameterSequence);
    }
    if ("in".equals(op)) {
      return column + " IN (" + bindList(node.getValues(), parameters, parameterSequence) + ")";
    }
    if ("notIn".equals(op)) {
      return column + " NOT IN (" + bindList(node.getValues(), parameters, parameterSequence) + ")";
    }
    if ("isNull".equals(op)) {
      return column + " IS NULL";
    }
    if ("isNotNull".equals(op)) {
      return column + " IS NOT NULL";
    }
    throw new IllegalArgumentException("filter-operator-invalid");
  }

  private String compileSort(List<SortSpec> sort, Map<String, String> allowedFields) {
    List<String> clauses = new ArrayList<String>();
    for (SortSpec sortSpec : sort) {
      if (sortSpec == null) {
        continue;
      }
      String column = columnFor(sortSpec.getField(), allowedFields, "sort-field-not-allowed");
      String direction = sortSpec.getDirection() == null ? "asc" : sortSpec.getDirection().trim().toLowerCase(Locale.ROOT);
      if (!"asc".equals(direction) && !"desc".equals(direction)) {
        throw new IllegalArgumentException("sort-direction-invalid");
      }
      clauses.add(column + " " + direction.toUpperCase(Locale.ROOT));
    }
    return String.join(", ", clauses);
  }

  private String columnFor(String field, Map<String, String> allowedFields, String errorCode) {
    if (isBlank(field)) {
      throw new IllegalArgumentException(errorCode);
    }
    String resolved = allowedFields.get(field.trim().toLowerCase(Locale.ROOT));
    if (resolved == null) {
      throw new IllegalArgumentException(errorCode + ": " + field);
    }
    if (!SIMPLE_IDENTIFIER.matcher(resolved).matches()) {
      throw new IllegalArgumentException(errorCode + ": " + field);
    }
    return FIELD_ALIAS + "." + resolved;
  }

  private String bind(Object value, Map<String, Object> parameters, ParameterNameSequence parameterSequence) {
    String name = parameterSequence.next();
    parameters.put(name, value);
    return ":" + name;
  }

  private String bindList(List<Object> values,
                          Map<String, Object> parameters,
                          ParameterNameSequence parameterSequence) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("filter-operator-invalid");
    }
    return values.stream()
        .map(value -> bind(value, parameters, parameterSequence))
        .collect(Collectors.joining(", "));
  }

  private void validatePage(PageSpec page, DatasetExecutionBudget budget) {
    if (page == null) {
      return;
    }
    if (page.getPageNo() == null || page.getPageNo() <= 0) {
      throw new IllegalArgumentException("page-invalid");
    }
    if (page.getPageSize() == null || page.getPageSize() <= 0 || page.getPageSize() > (budget == null ? DatasetExecutionPolicyProvider.current().maxPageSize() : budget.policy().maxPageSize())) {
      throw new com.ouroboros.exception.BusinessException("dataset.page-size-exceeded: 页大小超出有效上限");
    }
    long offset = ((long) page.getPageNo() - 1L) * page.getPageSize();
    if (offset > MAX_OFFSET) {
      throw new IllegalArgumentException("page-invalid");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private boolean isEmpty(List<?> values) {
    return values == null || values.isEmpty();
  }

  private static class ParameterNameSequence {
    private int value;

    private String next() {
      return "__dyn" + value++;
    }
  }
}
