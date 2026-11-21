package com.ouroboros.workflow.approval.external.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.ouroboros.data.config.DefaultSnowflakeNodeIdProviderConfig;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.MigrationStrategy;
import com.ouroboros.data.station.SqlDataStation;
import com.ouroboros.data.typed.runtime.TypedModelMetaHelper;
import com.ouroboros.security.rbac.entity.UserSsoEntity;
import com.ouroboros.workflow.approval.dynamicform.model.DynamicFormWorkflowBinding;

/**
 * MySQL gate for unique-constraint migration on real business models.
 *
 * <p>The first migration creates a legacy-shaped table without the newly
 * materialized unique constraint. Duplicate rows are then inserted through
 * JDBC. The real migration must fail before DDL and leave both data and
 * indexes unchanged; after cleanup it must add the expected key.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealBusinessModelMigrationMySqlIT {

  private static final String URL_PROPERTY = "ouroboros.mysql.url";
  private static final String USER_PROPERTY = "ouroboros.mysql.user";
  private static final String PASSWORD_ENVIRONMENT = "OUROBOROS_MYSQL_PASSWORD";

  private DriverManagerDataSource dataSource;
  private final List<String> tables = new ArrayList<>();

  @BeforeAll
  void connect() {
    new DefaultSnowflakeNodeIdProviderConfig();
    dataSource = new DriverManagerDataSource(
        requireProperty(URL_PROPERTY),
        requireProperty(USER_PROPERTY),
        requireEnvironment(PASSWORD_ENVIRONMENT));
  }

  @AfterAll
  void dropTables() throws SQLException {
    if (dataSource == null) {
      return;
    }
    try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
      for (String table : tables) {
        statement.execute("DROP TABLE IF EXISTS `" + table + "`");
      }
    }
  }

  @Test
  void dynamicFormBindingPreflightBlocksDuplicateLegacyRowsThenAddsUniqueKey() throws Exception {
    String table = tableName("dynamic_form_binding");
    DataModelMeta legacy = modelMeta(
        TypedModelMetaHelper.scanModelMate(
            "com.ouroboros.workflow.approval.dynamicform.model")
            .get("DynamicFormWorkflowBinding"),
        table);
    legacy.setUniqueConstraints(List.of());
    migrate(List.of(legacy));

    execute("INSERT INTO `" + table + "` "
        + "(id, instance_id, form_key, form_id, created_at, created_by) VALUES "
        + "('binding-1', 'instance-1', 'flow-a', 'form-a', ?, 'it'), "
        + "('binding-2', 'instance-1', 'flow-a', 'form-a', ?, 'it')",
        LocalDateTime.now(), LocalDateTime.now());

    DataModelMeta target = modelMeta(
        TypedModelMetaHelper.scanModelMate(
            "com.ouroboros.workflow.approval.dynamicform.model")
            .get("DynamicFormWorkflowBinding"),
        table);
    var rejected = migrateResult(List.of(target));
    assertThat(rejected.isLeft()).isTrue();
    assertThat(rejected.getLeft().getMessage()).contains("uk_awf_dynamic_form_binding");
    assertThat(rowCount(table)).isEqualTo(2);
    assertThat(nonPrimaryUniqueIndexes(table)).isEmpty();

    execute("DELETE FROM `" + table + "` WHERE id = 'binding-2'");
    migrateResultOrThrow(List.of(target));
    assertThat(nonPrimaryUniqueIndexes(table).values())
        .containsExactly(List.of("instance_id", "form_key", "form_id"));
  }

  @Test
  void userSsoPreflightBlocksDuplicateLegacyRowsThenAddsUniqueKey() throws Exception {
    String table = tableName("user_sso");
    DataModelMeta legacy = modelMeta(
        TypedModelMetaHelper.scanModelMate(
            "com.ouroboros.security.rbac.entity")
            .get("UserSsoEntity"),
        table);
    legacy.setUniqueConstraints(List.of());
    migrate(List.of(legacy));

    execute("INSERT INTO `" + table + "` "
        + "(id, user_identity, sso_channel, sso_user_identity) VALUES "
        + "('sso-1', 'user-1', 'dingtalk', 'external-1'), "
        + "('sso-2', 'user-2', 'dingtalk', 'external-1')");

    DataModelMeta target = modelMeta(
        TypedModelMetaHelper.scanModelMate(
            "com.ouroboros.security.rbac.entity")
            .get("UserSsoEntity"),
        table);
    var rejected = migrateResult(List.of(target));
    assertThat(rejected.isLeft()).isTrue();
    assertThat(rejected.getLeft().getMessage())
        .contains("un_userssoentity__ssochannel_ssouseridentity")
        .contains("dingtalk,external-1")
        .contains("count=2");
    assertThat(rowCount(table)).isEqualTo(2);
    assertThat(nonPrimaryUniqueIndexes(table)).isEmpty();

    execute("DELETE FROM `" + table + "` WHERE id = 'sso-2'");
    migrateResultOrThrow(List.of(target));
    assertThat(nonPrimaryUniqueIndexes(table).values())
        .containsExactly(List.of("sso_channel", "sso_user_identity"));
  }

  @ParameterizedTest
  @CsvSource({
      "com.ouroboros.workflow.approval.external.runtime.start, ExternalStartFact",
      "com.ouroboros.workflow.approval.external.runtime.binding, ExternalResultBinding"
  })
  void externalStartUpgradeAllowsAnOptionalBusinessContextAndPreservesUniqueIndexes(
      String modelPackage, String modelName) throws Exception {
    String table = tableName("start_context");
    DataModelMeta source = TypedModelMetaHelper.scanModelMate(modelPackage).get(modelName);
    DataModelMeta legacy = modelMeta(source, table);
    legacy.getFields().stream()
        .filter(field -> List.of("businessRecordType", "businessRecordId").contains(field.getName()))
        .forEach(field -> field.setIsNullable(false));
    migrate(List.of(legacy));
    assertThat(businessContextNullability(table)).containsExactly(false, false);
    Map<String, List<String>> indexes = nonPrimaryUniqueIndexes(table);

    migrateResultOrThrow(List.of(modelMeta(source, table)));

    assertThat(businessContextNullability(table)).containsExactly(true, true);
    assertThat(nonPrimaryUniqueIndexes(table)).isEqualTo(indexes);
  }

  private List<Boolean> businessContextNullability(String table) throws SQLException {
    try (var connection = dataSource.getConnection();
        var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, "business_record_%")) {
      List<Boolean> nullable = new ArrayList<>();
      while (columns.next()) {
        nullable.add(columns.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable);
      }
      return nullable;
    }
  }

  private DataModelMeta modelMeta(DataModelMeta source, String table) {
    assertThat(source).isNotNull();
    DataModelMeta copy = source.deepCopy();
    copy.setName(source.getName());
    copy.setRawName(table);
    copy.setMigrationStrategy(MigrationStrategy.AUTO);
    tables.add(table);
    return copy;
  }

  private String tableName(String suffix) {
    return "t_awf_it_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
  }

  private void migrate(List<DataModelMeta> models) {
    migrateResultOrThrow(models);
  }

  private void migrateResultOrThrow(List<DataModelMeta> models) {
    var result = migrateResult(models);
    assertThat(result.isRight()).as("migration result: %s", result).isTrue();
  }

  private io.vavr.control.Either<com.ouroboros.data.exception.DataModelException, Boolean> migrateResult(
      List<DataModelMeta> models) {
    return new SqlDataStation(
        "real-business-model-migration-it",
        dataSource,
        models,
        MigrationStrategy.AUTO,
        "MySQL").migrate();
  }

  private void execute(String sql, Object... parameters) throws SQLException {
    try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      statement.executeUpdate();
    }
  }

  private long rowCount(String table) throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement();
        var result = statement.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
      result.next();
      return result.getLong(1);
    }
  }

  private Map<String, List<String>> nonPrimaryUniqueIndexes(String table) throws SQLException {
    try (var connection = dataSource.getConnection();
        var statement = connection.prepareStatement(
            "SELECT index_name, column_name FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? "
                + "AND non_unique = 0 AND index_name <> 'PRIMARY' "
                + "ORDER BY index_name, seq_in_index")) {
      statement.setString(1, table);
      try (var result = statement.executeQuery()) {
        Map<String, List<String>> indexes = new java.util.LinkedHashMap<>();
        while (result.next()) {
          indexes.computeIfAbsent(result.getString("index_name"), ignored -> new ArrayList<>())
              .add(result.getString("column_name"));
        }
        return indexes;
      }
    }
  }

  private String requireProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("mysql-integration requires -D" + name);
    }
    return value;
  }

  private String requireEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("mysql-integration requires environment variable " + name);
    }
    return value;
  }
}
