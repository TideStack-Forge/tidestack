package com.ouroboros.statistics.reporting.validation;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlDialectDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetDefinitionValidatorTest {

  @Test
  void validatesDataScopeTargetsAndContextReferences() {
    var dataset = sqlDataset("demo.scoped");
    dataset.getPermissionDefinition().put("scopeBindings", java.util.Map.of("missing", "departmentId"));
    assertThrows(IllegalArgumentException.class, () -> new DatasetDefinitionValidator().validate(dataset));
    dataset.getPermissionDefinition().put("scopeBindings", "invalid");
    assertThrows(IllegalArgumentException.class, () -> new DatasetDefinitionValidator().validate(dataset));
    dataset.getPermissionDefinition().put("scopeBindings", java.util.Map.of());
    assertDoesNotThrow(() -> new DatasetDefinitionValidator().validate(dataset));
    dataset.setParameters(java.util.List.of(parameter("departmentId", "string")));
    ((SqlQueryDefinition) dataset.getQueryDefinition()).setSql("select ${departmentId} as total");
    dataset.getPermissionDefinition().put("scopeBindings", java.util.Map.of("departmentId", "departmentId"));
    assertDoesNotThrow(() -> new DatasetDefinitionValidator().validate(dataset));
    dataset.getPermissionDefinition().put("scopeBindings", java.util.Map.of("departmentId", " "));
    assertThrows(IllegalArgumentException.class, () -> new DatasetDefinitionValidator().validate(dataset));
  }

  @Test
  void shouldRejectNamesThatCannotIdentifyOneResourceOrRoute() {
    for (String name : java.util.List.of("../sales", "demo/sales", "demo..sales", "demo{sales}", "demo sales")) {
      assertThrows(IllegalArgumentException.class, () -> new DatasetDefinitionValidator().validate(sqlDataset(name)));
    }
  }

  @Test
  void shouldRejectDuplicateSchemaFields() {
    var dataset = sqlDataset("demo.sales");
    dataset.setSchema(schema(field("total"), field("total")));

    var error = assertThrows(IllegalArgumentException.class,
        () -> new DatasetDefinitionValidator().validate(dataset));

    assertTrue(error.getMessage().contains("schema"));
  }

  @Test
  void shouldRejectSqlDatasetWithoutExplicitSchema() {
    var dataset = sqlDataset("demo.sales");
    dataset.setSchema(new DatasetSchemaDefinition());

    var error = assertThrows(IllegalArgumentException.class,
        () -> new DatasetDefinitionValidator().validate(dataset));

    assertTrue(error.getMessage().contains("schema"));
  }

  @Test
  void shouldRejectDuplicateSqlDialects() {
    var dataset = sqlDataset("demo.sales");
    var sqlQuery = (SqlQueryDefinition) dataset.getQueryDefinition();
    sqlQuery.setSql(null);
    sqlQuery.setDialects(Arrays.asList(
        dialect("mysql"),
        dialect("mysql")
    ));

    var error = assertThrows(IllegalArgumentException.class,
        () -> new DatasetDefinitionValidator().validate(dataset));

    assertTrue(error.getMessage().contains("duplicate"));
  }

  @Test
  void shouldRequireSqlPlaceholdersToHaveDeclaredParameters() {
    var dataset = sqlDataset("demo.sales");
    ((SqlQueryDefinition) dataset.getQueryDefinition()).setSql("select ${missing} as total");

    var error = assertThrows(IllegalArgumentException.class,
        () -> new DatasetDefinitionValidator().validate(dataset));

    assertTrue(error.getMessage().contains("undeclared"));
  }

  @Test
  void shouldRejectDeclaredParametersThatAreNotReferencedBySql() {
    var dataset = sqlDataset("demo.sales");
    dataset.setParameters(Collections.singletonList(parameter("unused", "string")));

    var error = assertThrows(IllegalArgumentException.class,
        () -> new DatasetDefinitionValidator().validate(dataset));

    assertTrue(error.getMessage().contains("not referenced"));
  }

  @Test
  void shouldValidateCommonAndDialectPlaceholderUnion() {
    var dataset = sqlDataset("demo.sales");
    var query = (SqlQueryDefinition) dataset.getQueryDefinition();
    query.setSql("select ${commonValue} as total");
    query.setDialects(Collections.singletonList(dialect("mysql", "select ${mysqlValue} as total")));
    dataset.setParameters(Arrays.asList(
        parameter("commonValue", "string"),
        parameter("mysqlValue", "number")));

    assertDoesNotThrow(() -> new DatasetDefinitionValidator().validate(dataset));
  }

  @Test
  void shouldRejectInvalidParameterNamesAndPlaceholders() {
    var invalidNameDataset = sqlDataset("demo.sales");
    ((SqlQueryDefinition) invalidNameDataset.getQueryDefinition()).setSql("select ${valid} as total");
    invalidNameDataset.setParameters(Collections.singletonList(parameter("bad-name", "string")));
    var invalidName = assertThrows(IllegalArgumentException.class,
        () -> new DatasetDefinitionValidator().validate(invalidNameDataset));
    assertTrue(invalidName.getMessage().contains("valid identifier"));

    var invalidPlaceholderDataset = sqlDataset("demo.sales");
    ((SqlQueryDefinition) invalidPlaceholderDataset.getQueryDefinition()).setSql("select ${bad-name} as total");
    var invalidPlaceholder = assertThrows(IllegalArgumentException.class,
        () -> new DatasetDefinitionValidator().validate(invalidPlaceholderDataset));
    assertTrue(invalidPlaceholder.getMessage().contains("invalid parameter placeholder"));
  }

  @Test
  void shouldRejectUnsupportedParameterContractCombinations() {
    assertParameterRejected(parameter("keyword", "text"), "unsupported parameter type");
    assertParameterRejected(parameter("keyword", null), "unsupported parameter type");

    var unsupportedOperator = parameter("keyword", "string");
    unsupportedOperator.setOperator("between");
    assertParameterRejected(unsupportedOperator, "unsupported parameter operator");

    var nonStringLike = parameter("keyword", "number");
    nonStringLike.setOperator("like");
    assertParameterRejected(nonStringLike, "like operator requires string");

    var multiValue = parameter("keyword", "string");
    multiValue.setMultiValue(true);
    assertParameterRejected(multiValue, "multiValue");

    var inWithoutMultiValue = parameter("keyword", "string");
    inWithoutMultiValue.setOperator("in");
    assertParameterRejected(inWithoutMultiValue, "requires multiValue");

    var requiredOmit = parameter("keyword", "string");
    requiredOmit.setRequired(true);
    requiredOmit.setAllowEmptyOmitCondition(true);
    assertParameterRejected(requiredOmit, "allowEmptyOmitCondition");

    var bindingWithoutSource = parameter("keyword", "string");
    bindingWithoutSource.setSystemContextBinding("tenantId");
    assertParameterRejected(bindingWithoutSource, "systemContextBinding");

    var requestWithBinding = parameter("keyword", "string");
    requestWithBinding.setValueSource("request");
    requestWithBinding.setSystemContextBinding("tenantId");
    assertParameterRejected(requestWithBinding, "cannot declare systemContextBinding");

    var contextWithoutBinding = parameter("keyword", "string");
    contextWithoutBinding.setValueSource("systemContext");
    assertParameterRejected(contextWithoutBinding, "requires systemContextBinding");

    var unsupportedSource = parameter("keyword", "string");
    unsupportedSource.setValueSource("userInput");
    assertParameterRejected(unsupportedSource, "unsupported parameter valueSource");
  }

  @Test
  void shouldAllowValidParameterContract() {
    var parameter = parameter("keyword", "string");
    parameter.setOperator("like");
    parameter.setValueSource("request");
    assertParameterAccepted(parameter);

    var contextParameter = parameter("tenantId", "string");
    contextParameter.setValueSource("systemContext");
    contextParameter.setSystemContextBinding("tenantId");
    assertParameterAccepted(contextParameter);

    var multiValue = parameter("region", "string");
    multiValue.setOperator("notIn");
    multiValue.setMultiValue(true);
    assertParameterAccepted(multiValue);
  }

  private static void assertParameterRejected(ParameterDefinition parameter, String message) {
    var dataset = sqlDataset("demo.sales");
    String name = parameter.getName();
    ((SqlQueryDefinition) dataset.getQueryDefinition()).setSql("select ${" + name + "} as total");
    dataset.setParameters(Collections.singletonList(parameter));

    var error = assertThrows(IllegalArgumentException.class,
        () -> new DatasetDefinitionValidator().validate(dataset));
    assertTrue(error.getMessage().contains(message));
  }

  private static void assertParameterAccepted(ParameterDefinition parameter) {
    var dataset = sqlDataset("demo.sales");
    String name = parameter.getName();
    ((SqlQueryDefinition) dataset.getQueryDefinition()).setSql("select ${" + name + "} as total");
    dataset.setParameters(Collections.singletonList(parameter));
    assertDoesNotThrow(() -> new DatasetDefinitionValidator().validate(dataset));
  }

  private static DatasetDefinition sqlDataset(String name) {
    var dataset = new DatasetDefinition();
    dataset.setName(name);
    var queryDefinition = new SqlQueryDefinition();
    queryDefinition.setSql("select 1 as total");
    dataset.setQueryDefinition(queryDefinition);
    dataset.setSchema(schema(field("total")));
    return dataset;
  }

  private static ParameterDefinition parameter(String name, String type) {
    var parameter = new ParameterDefinition();
    parameter.setName(name);
    parameter.setType(type);
    return parameter;
  }

  private static DatasetSchemaDefinition schema(DatasetFieldDefinition... fields) {
    var schema = new DatasetSchemaDefinition();
    schema.setFields(Arrays.asList(fields));
    return schema;
  }

  private static DatasetFieldDefinition field(String name) {
    var field = new DatasetFieldDefinition();
    field.setName(name);
    field.setDataType("number");
    field.setSemanticRole("metric");
    return field;
  }

  private static SqlDialectDefinition dialect(String dialectName) {
    return dialect(dialectName, "select 1 as total");
  }

  private static SqlDialectDefinition dialect(String dialectName, String sql) {
    var dialect = new SqlDialectDefinition();
    dialect.setDialectName(dialectName);
    dialect.setSql(sql);
    return dialect;
  }
}
