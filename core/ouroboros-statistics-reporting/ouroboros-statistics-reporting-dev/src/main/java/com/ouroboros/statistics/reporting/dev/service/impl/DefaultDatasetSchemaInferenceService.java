package com.ouroboros.statistics.reporting.dev.service.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.stereotype.Service;

import com.ouroboros.dev.datasource.model.DataSourceDefine;
import com.ouroboros.model.annotation.AllowDirectJDBC;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.dev.model.DatasetInferSchemaRequest;
import com.ouroboros.statistics.reporting.dev.model.editor.DatasetInferSchemaResult;
import com.ouroboros.statistics.reporting.dev.service.DatasetSchemaInferenceService;
import com.ouroboros.util.Maps;

@Service
public class DefaultDatasetSchemaInferenceService implements DatasetSchemaInferenceService {

  private final com.ouroboros.statistics.reporting.dev.service.DatasetDesignDataSourceService dataSources;

  public DefaultDatasetSchemaInferenceService(
      com.ouroboros.statistics.reporting.dev.service.DatasetDesignDataSourceService dataSources) {
    this.dataSources = Objects.requireNonNull(dataSources, "dataSources is required");
  }

  @Override
  public DatasetInferSchemaResult inferSchema(String appName, DatasetInferSchemaRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("sql-invalid: request is required");
    }
    String sql = resolveSql(request);
    if (isBlank(sql)) {
      throw new IllegalArgumentException("sql-invalid: sql is required");
    }
    Optional<DataSourceDefine> connection = dataSources.connection(appName, request);
    return connection.isPresent()
        ? inferFromMetadata(connection.get(), sql, resolveParameters(request))
        : inferFromModels(appName, request);
  }

  @Override
  @AllowDirectJDBC("connectivity check for statistics dataset authoring")
  public void testConnection(String appName, DatasetInferSchemaRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("datasource-not-found: request is required");
    }
    DataSourceDefine dataSourceDefine = resolveDataSourceDefine(appName, request);
    if (!"jdbc".equalsIgnoreCase(dataSourceDefine.getType())) {
      throw new IllegalArgumentException(
          "datasource-unreachable: unsupported datasource type " + dataSourceDefine.getType());
    }
    loadDriver(dataSourceDefine);
    try (Connection ignored = openConnection(dataSourceDefine)) {
      // Opening and closing the connection is the connectivity contract.
    } catch (SQLException error) {
      throw datasourceUnreachable(error);
    }
  }

  private DataSourceDefine resolveDataSourceDefine(String appName, DatasetInferSchemaRequest request) {
    return dataSources.connection(appName, request).orElseThrow(() ->
        new com.ouroboros.exception.BusinessException("请选择实际连接或应用运行容器"));
  }

  private String resolveSql(DatasetInferSchemaRequest request) {
    if (!isBlank(request.getDialectName()) && request.getDialects() != null) {
      for (var dialect : request.getDialects()) {
        if (dialect != null
            && request.getDialectName().trim().equalsIgnoreCase(dialect.getDialectName())
            && !isBlank(dialect.getSql())) {
          return dialect.getSql();
        }
      }
      throw new IllegalArgumentException(
          "sql-invalid: dialect SQL is required for " + request.getDialectName());
    }
    return request.getSql();
  }

  private Map<String, Object> resolveParameters(DatasetInferSchemaRequest request) {
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    if (request.getParameterDefinitions() != null) {
      for (ParameterDefinition definition : request.getParameterDefinitions()) {
        if (definition != null && !isBlank(definition.getName())) {
          parameters.put(definition.getName(), definition.getDefaultValue());
        }
      }
    }
    if (request.getParameters() != null) {
      parameters.putAll(request.getParameters());
    }
    return parameters;
  }

  @AllowDirectJDBC("request-scoped, metadata-only schema for offline dataset SQL inference")
  private DatasetInferSchemaResult inferFromModels(String appName, DatasetInferSchemaRequest request) {
    var models = dataSources.models(appName, request.getDatasource());
    var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
        "jdbc:h2:mem:dataset_schema_" + java.util.UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE", "sa", "");
    try (Connection connection = source.getConnection()) {
      var migration = com.ouroboros.data.migration.SqlMigrationService.getDefault()
          .generateMigrationSql(source, "", "dataset-schema-inference", models);
      if (migration.isLeft()) {
        throw new BusinessException("metadata-invalid: 数据模型结构无法用于字段推导", migration.getLeft());
      }
      try (var ddl = connection.createStatement()) {
        for (String sql : migration.get()) {
          ddl.execute(sql);
        }
      }
      try (PreparedStatement statement = createStatement(connection, metadataOnlySql(resolveSql(request)), resolveParameters(request))) {
        DatasetInferSchemaResult result = toResult(statement.getMetaData());
        result.getWarnings().add("字段根据数据模型元数据推导，尚未验证实际数据库；方言专用表达式请使用测试连接推导。");
        return result;
      }
    } catch (SQLException error) {
      throw new BusinessException("sql-invalid: 无法根据数据模型推导字段，请检查表和字段名，或选择实际测试连接", error);
    }
  }

  @AllowDirectJDBC("metadata introspection for statistics dataset schema inference")
  private DatasetInferSchemaResult inferFromMetadata(DataSourceDefine dataSourceDefine,
                                                     String sql,
                                                     Map<String, Object> parameters) {
    if (!"jdbc".equalsIgnoreCase(dataSourceDefine.getType())) {
      throw new IllegalArgumentException("datasource-unreachable: unsupported datasource type " + dataSourceDefine.getType());
    }

    loadDriver(dataSourceDefine);

    try (Connection connection = openConnection(dataSourceDefine);
         PreparedStatement statement = createStatement(connection, metadataOnlySql(sql), parameters)) {
      ResultSetMetaData metadata = statement.getMetaData();
      if (metadata == null) {
        try {
          statement.executeQuery().close();
          metadata = statement.getMetaData();
        } catch (SQLException error) {
          throw sqlInvalid(error);
        }
      }
      return toResult(metadata);
    } catch (IllegalArgumentException error) {
      throw error;
    } catch (SQLException error) {
      throw sqlInvalid(error);
    } catch (RuntimeException error) {
      throw sqlInvalid(error);
    }
  }

  private String metadataOnlySql(String sql) {
    return "select * from (" + sql + ") dataset_schema_infer where 1 = 0";
  }

  private Connection openConnection(DataSourceDefine dataSourceDefine) {
    String url = stringProperty(dataSourceDefine, "url");
    if (isBlank(url)) {
      throw new IllegalArgumentException("datasource-unreachable: JDBC URL is required");
    }
    Properties properties = new Properties();
    Object connectionProperties = dataSourceDefine.getProperties().get("properties");
    if (connectionProperties instanceof Map) {
      ((Map<?, ?>) connectionProperties).forEach((key, value) -> {
        if (key != null && value != null) {
          properties.setProperty(String.valueOf(key), String.valueOf(value));
        }
      });
    }
    properties.setProperty("user", stringProperty(dataSourceDefine, "username"));
    properties.setProperty("password", stringProperty(dataSourceDefine, "password"));
    try {
      return DriverManager.getConnection(url, properties);
    } catch (SQLException error) {
      throw datasourceUnreachable(error);
    }
  }

  private void loadDriver(DataSourceDefine dataSourceDefine) {
    String driverClassName = stringProperty(dataSourceDefine, "driverClassName");
    if (isBlank(driverClassName)) {
      return;
    }
    try {
      Class.forName(driverClassName);
    } catch (ClassNotFoundException error) {
      throw datasourceUnreachable(error);
    }
  }

  private PreparedStatement createStatement(Connection connection,
                                            String sql,
                                            Map<String, Object> parameters) throws SQLException {
    var parsedSql = NamedParameterUtils.parseSqlStatement(toNamedParameterSql(sql));
    var parameterSource = new MapSqlParameterSource(parameters == null ? java.util.Collections.emptyMap() : parameters);
    String sqlToUse = NamedParameterUtils.substituteNamedParameters(parsedSql, parameterSource);
    Object[] values = NamedParameterUtils.buildValueArray(parsedSql, parameterSource, null);
    PreparedStatement statement = connection.prepareStatement(sqlToUse);
    for (int index = 0; index < values.length; index++) {
      statement.setObject(index + 1, values[index]);
    }
    return statement;
  }

  private String toNamedParameterSql(String sql) {
    if (sql == null) {
      return null;
    }
    return sql.replaceAll("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}", ":$1");
  }

  private DatasetInferSchemaResult toResult(ResultSetMetaData metadata) throws SQLException {
    DatasetInferSchemaResult result = new DatasetInferSchemaResult();
    List<DatasetFieldDefinition> fields = new ArrayList<DatasetFieldDefinition>();
    List<String> warnings = new ArrayList<String>();
    for (int index = 1; index <= metadata.getColumnCount(); index++) {
      int jdbcType = metadata.getColumnType(index);
      String typeName = metadata.getColumnTypeName(index);
      MappedType mappedType = mapJdbcType(jdbcType, typeName);
      DatasetFieldDefinition field = new DatasetFieldDefinition();
      field.setName(columnName(metadata, index));
      field.setTitle(field.getName());
      field.setDataType(mappedType.getDataType());
      field.setSemanticRole(null);
      field.setNullable(Boolean.valueOf(metadata.isNullable(index) != ResultSetMetaData.columnNoNulls));
      fields.add(field);
      if (mappedType.getWarning() != null) {
        warnings.add(mappedType.getWarning());
      }
    }
    result.setFields(fields);
    result.setWarnings(warnings);
    return result;
  }

  private String columnName(ResultSetMetaData metadata, int index) throws SQLException {
    String label = metadata.getColumnLabel(index);
    if (!isBlank(label)) {
      return label;
    }
    return metadata.getColumnName(index);
  }

  private MappedType mapJdbcType(int jdbcType, String typeName) {
    switch (jdbcType) {
      case Types.CHAR:
      case Types.VARCHAR:
      case Types.LONGVARCHAR:
      case Types.NCHAR:
      case Types.NVARCHAR:
      case Types.LONGNVARCHAR:
        return new MappedType("string", null);
      case Types.TINYINT:
      case Types.SMALLINT:
      case Types.INTEGER:
      case Types.BIGINT:
        return new MappedType("integer", null);
      case Types.FLOAT:
      case Types.REAL:
      case Types.DOUBLE:
      case Types.NUMERIC:
      case Types.DECIMAL:
        return new MappedType("number", null);
      case Types.DATE:
      case Types.TIME:
      case Types.TIMESTAMP:
      case Types.TIME_WITH_TIMEZONE:
      case Types.TIMESTAMP_WITH_TIMEZONE:
      case -101:
      case -102:
        return new MappedType("datetime", null);
      case Types.BOOLEAN:
      case Types.BIT:
        return new MappedType("boolean", null);
      default:
        return new MappedType("string",
            "JDBC type " + jdbcType + "/" + typeName + " cannot be mapped exactly; fallback to string.");
    }
  }

  private RuntimeException sqlInvalid(Exception error) {
    return new IllegalArgumentException("sql-invalid: " + error.getMessage(), error);
  }

  private RuntimeException datasourceUnreachable(Exception error) {
    return new IllegalArgumentException("datasource-unreachable: " + error.getMessage(), error);
  }



  private String stringProperty(DataSourceDefine dataSourceDefine, String key) {
    return Maps.getAsOrDefault(dataSourceDefine.getProperties(), key, String.class, "");
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static class MappedType {
    private final String dataType;
    private final String warning;

    private MappedType(String dataType, String warning) {
      this.dataType = dataType;
      this.warning = warning;
    }

    private String getDataType() {
      return dataType;
    }

    private String getWarning() {
      return warning;
    }
  }
}
