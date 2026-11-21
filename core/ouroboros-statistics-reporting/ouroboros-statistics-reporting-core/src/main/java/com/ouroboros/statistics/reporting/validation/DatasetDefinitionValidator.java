package com.ouroboros.statistics.reporting.validation;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.definition.query.QueryDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlDialectDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;

public class DatasetDefinitionValidator {
  private static final Pattern PARAMETER_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Set<String> PARAMETER_TYPES = Set.of(
      "string", "number", "date", "datetime", "boolean");
  private static final Set<String> PARAMETER_OPERATORS = Set.of(
      "eq", "ne", "gt", "gte", "lt", "lte", "in", "notIn", "like");
  private static final Set<String> VALUE_SOURCES = Set.of("request", "systemContext");

  public void validate(DatasetDefinition dataset) {
    validate(dataset, false);
  }

  public void validate(DatasetDefinition dataset, boolean enforcePlatformLimits) {
    if (dataset == null) {
      throw new IllegalArgumentException("dataset definition is required");
    }
    validateName(dataset.getName());
    Set<String> referencedParameters = validateQueryDefinition(dataset.getQueryDefinition());
    validateParameters(dataset.getParameters(), referencedParameters);
    validateSchema(dataset.getSchema(), dataset.getQueryDefinition());
    validateScopeBindings(dataset);
    var policy = enforcePlatformLimits
        ? com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionPolicyProvider.current()
        : com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionPolicy.defaults();
    policy.tighten(dataset.getExecutionConstraints(), enforcePlatformLimits);
  }

  private void validateScopeBindings(DatasetDefinition dataset) {
    Object raw = dataset.getPermissionDefinition().get("scopeBindings");
    if (raw == null) { return; }
    if (!(raw instanceof java.util.Map<?, ?> bindings)) {
      throw new IllegalArgumentException("permissionDefinition.scopeBindings 必须为参数与账户上下文的映射");
    }
    Set<String> parameters = new HashSet<>();
    dataset.getParameters().forEach(parameter -> parameters.add(parameter.getName()));
    bindings.forEach((name, value) -> {
      if (!(name instanceof String parameter) || !parameters.contains(parameter)) {
        throw new IllegalArgumentException("permissionDefinition.scopeBindings 查询参数不存在: " + name);
      }
      if (!(value instanceof String context) || context.isBlank()) {
        throw new IllegalArgumentException("permissionDefinition.scopeBindings 账户上下文不能为空: " + name);
      }
    });
  }

  public static void validateName(String name) {
    StatisticsDefinitionName.validate(name, "dataset");
  }

  private Set<String> validateQueryDefinition(QueryDefinition queryDefinition) {
    if (queryDefinition == null) {
      throw new IllegalArgumentException("queryDefinition is required");
    }
    if (queryDefinition instanceof SqlQueryDefinition) {
      return validateSqlQuery((SqlQueryDefinition) queryDefinition);
    }
    throw new IllegalArgumentException("unsupported queryDefinition type: " + queryDefinition.getType());
  }

  private Set<String> validateSqlQuery(SqlQueryDefinition queryDefinition) {
    if (isBlank(queryDefinition.getSql())
        && (queryDefinition.getDialects() == null || queryDefinition.getDialects().isEmpty())) {
      throw new IllegalArgumentException("sql query must declare sql or dialects");
    }
    Set<String> referencedParameters = new LinkedHashSet<String>();
    if (!isBlank(queryDefinition.getSql())) {
      referencedParameters.addAll(parseParameterReferences(queryDefinition.getSql(), "sql"));
    }
    referencedParameters.addAll(validateSqlDialects(queryDefinition.getDialects()));
    return referencedParameters;
  }

  private Set<String> validateSqlDialects(List<SqlDialectDefinition> dialects) {
    Set<String> referencedParameters = new LinkedHashSet<String>();
    if (dialects == null) {
      return referencedParameters;
    }
    Set<String> dialectNames = new HashSet<String>();
    for (SqlDialectDefinition dialect : dialects) {
      if (dialect == null) {
        continue;
      }
      if (isBlank(dialect.getDialectName())) {
        throw new IllegalArgumentException("sql dialect dialectName is required");
      }
      if (isBlank(dialect.getSql())) {
        throw new IllegalArgumentException("sql dialect sql is required");
      }
      String dialectName = safe(dialect.getDialectName());
      if (!dialectNames.add(dialectName)) {
        throw new IllegalArgumentException("sql dialect contains duplicate dialectName: " + dialectName);
      }
      referencedParameters.addAll(parseParameterReferences(
          dialect.getSql(), "dialect " + dialectName));
    }
    return referencedParameters;
  }

  private void validateParameters(List<ParameterDefinition> parameters, Set<String> referencedParameters) {
    Set<String> names = new HashSet<>();
    if (parameters != null) {
      for (ParameterDefinition parameter : parameters) {
        if (parameter == null || isBlank(parameter.getName())) {
          throw new IllegalArgumentException("parameters must declare a name");
        }
        String name = parameter.getName();
        if (!PARAMETER_NAME.matcher(name).matches()) {
          throw new IllegalArgumentException("parameter name is not a valid identifier: " + name);
        }
        if (!names.add(name)) {
          throw new IllegalArgumentException("parameters contains duplicate name: " + name);
        }
        validateParameterContract(parameter);
      }
    }
    Set<String> undeclared = new LinkedHashSet<String>(referencedParameters);
    undeclared.removeAll(names);
    if (!undeclared.isEmpty()) {
      throw new IllegalArgumentException("sql contains undeclared parameter: " + undeclared.iterator().next());
    }
    Set<String> unused = new LinkedHashSet<String>(names);
    unused.removeAll(referencedParameters);
    if (!unused.isEmpty()) {
      throw new IllegalArgumentException("parameter is not referenced by sql: " + unused.iterator().next());
    }
  }

  private void validateParameterContract(ParameterDefinition parameter) {
    String type = normalized(parameter.getType());
    if (type == null || !PARAMETER_TYPES.contains(type)) {
      throw new IllegalArgumentException("unsupported parameter type: " + parameter.getType());
    }

    String operator = parameter.getOperator();
    if (!isBlank(operator) && !PARAMETER_OPERATORS.contains(operator)) {
      throw new IllegalArgumentException("unsupported parameter operator: " + operator);
    }
    if ("like".equals(operator) && !"string".equals(type)) {
      throw new IllegalArgumentException("like operator requires string parameter: " + parameter.getName());
    }
    if (parameter.isMultiValue() && !isInOperator(operator)) {
      throw new IllegalArgumentException("multiValue parameter requires in or notIn operator: "
          + parameter.getName());
    }
    if (isInOperator(operator) && !parameter.isMultiValue()) {
      throw new IllegalArgumentException("in or notIn operator requires multiValue: " + parameter.getName());
    }
    if (parameter.isAllowEmptyOmitCondition() && parameter.isRequired()) {
      throw new IllegalArgumentException("allowEmptyOmitCondition cannot be used with required parameter: "
          + parameter.getName());
    }

    String valueSource = parameter.getValueSource();
    if (!isBlank(valueSource) && !VALUE_SOURCES.contains(valueSource)) {
      throw new IllegalArgumentException("unsupported parameter valueSource: " + valueSource);
    }
    boolean hasBinding = !isBlank(parameter.getSystemContextBinding());
    if ("request".equals(valueSource) && hasBinding) {
      throw new IllegalArgumentException("request valueSource cannot declare systemContextBinding: "
          + parameter.getName());
    }
    if (hasBinding && !"systemContext".equals(valueSource)) {
      throw new IllegalArgumentException("systemContextBinding requires valueSource systemContext: "
          + parameter.getName());
    }
    if ("systemContext".equals(valueSource) && !hasBinding) {
      throw new IllegalArgumentException("systemContext valueSource requires systemContextBinding: "
          + parameter.getName());
    }
  }

  private Set<String> parseParameterReferences(String sql, String location) {
    Set<String> references = new LinkedHashSet<String>();
    int offset = 0;
    while (offset < sql.length()) {
      int start = sql.indexOf("${", offset);
      if (start < 0) {
        break;
      }
      int end = sql.indexOf('}', start + 2);
      if (end < 0) {
        throw new IllegalArgumentException(location + " contains an unterminated parameter placeholder");
      }
      String name = sql.substring(start + 2, end);
      if (!PARAMETER_NAME.matcher(name).matches()) {
        throw new IllegalArgumentException(location + " contains an invalid parameter placeholder: ${" + name + "}");
      }
      references.add(name);
      offset = end + 1;
    }
    return references;
  }

  private boolean isInOperator(String operator) {
    return "in".equals(operator) || "notIn".equals(operator);
  }

  private String normalized(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  private void validateSchema(DatasetSchemaDefinition schema, QueryDefinition queryDefinition) {
    if (schema == null || schema.getFields() == null || schema.getFields().isEmpty()) {
      throw new IllegalArgumentException("schema must declare at least one field");
    }
    Set<String> fieldNames = new HashSet<>();
    for (DatasetFieldDefinition field : schema.getFields()) {
      if (field == null || isBlank(field.getName())) {
        throw new IllegalArgumentException("schema field name is required");
      }
      if (isBlank(field.getDataType()) || isBlank(field.getSemanticRole())) {
        throw new IllegalArgumentException("schema field dataType and semanticRole are required");
      }
      if (!fieldNames.add(field.getName().toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("schema contains duplicate field: " + field.getName());
      }
    }
    if (queryDefinition instanceof SqlQueryDefinition && schema.getFields().isEmpty()) {
      throw new IllegalArgumentException("sql dataset schema must declare fields");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private String safe(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }
}
