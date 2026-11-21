package com.ouroboros.statistics.reporting.runtime.execution;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.ouroboros.model.annotation.AllowDirectJDBC;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;

public class SpringSqlDataSourceProvider implements SqlDataSourceProvider {
  private static final Logger logger = LoggerFactory.getLogger(SpringSqlDataSourceProvider.class);
  
  private final Map<String, DataSource> dataSources;
  private final ObjectProvider<DataSource> uniqueDataSourceProvider;

  public SpringSqlDataSourceProvider(Map<String, DataSource> dataSources,
                                     ObjectProvider<DataSource> uniqueDataSourceProvider) {
    this.dataSources = dataSources == null ? Collections.<String, DataSource>emptyMap() : new LinkedHashMap<String, DataSource>(dataSources);
    this.uniqueDataSourceProvider = uniqueDataSourceProvider;
  }

  @Override
  public SqlDataSourceContext resolve(SqlQueryDefinition queryDefinition, DatasetExecutionRequest request) {
    if (queryDefinition == null) {
      throw new IllegalArgumentException("sql query definition is required");
    }

    Map<String, Object> executionContext = request == null || request.getExecutionContext() == null
        ? Collections.<String, Object>emptyMap()
        : request.getExecutionContext();

    ResolvedDataSource resolvedDataSource = resolveDataSource(queryDefinition, executionContext);
    SqlDataSourceContext context = new SqlDataSourceContext()
        .setDatasource(resolvedDataSource.getName())
        .setDataSource(resolvedDataSource.getDataSource());

    String dialect = stringValue(executionContext.get("dialect"));
    String dialectVersion = stringValue(executionContext.get("dialectVersion"));
    if (!isBlank(dialect)) {
      context.setDialect(normalizeDialect(dialect));
    }
    if (!isBlank(dialectVersion)) {
      context.setDialectVersion(dialectVersion);
    }
    if (isBlank(context.getDialect()) || isBlank(context.getDialectVersion())) {
      fillMetadata(context);
    }
    return context;
  }

  private ResolvedDataSource resolveDataSource(SqlQueryDefinition queryDefinition,
                                               Map<String, Object> executionContext) {
    String datasource = stringValue(executionContext.get("datasource"));
    if (isBlank(datasource) && queryDefinition != null) {
      datasource = queryDefinition.getDatasource();
    }
    DataSource inlineJdbcDataSource = createInlineJdbcDataSource(executionContext.get("jdbcConnection"));
    if (inlineJdbcDataSource != null) {
      return new ResolvedDataSource(isBlank(datasource) ? "default" : datasource, inlineJdbcDataSource);
    }
    if (!isBlank(datasource)) {
      DataSource dataSource = dataSources.get(datasource);
      if (dataSource == null && "default".equalsIgnoreCase(datasource)) {
        dataSource = resolveDefaultDataSource();
      }
      if (dataSource == null) {
        throw new IllegalArgumentException("sql datasource not found: " + datasource);
      }
      return new ResolvedDataSource(datasource, dataSource);
    }

    if (dataSources.size() == 1) {
      Map.Entry<String, DataSource> only = dataSources.entrySet().iterator().next();
      return new ResolvedDataSource(only.getKey(), only.getValue());
    }

    DataSource unique = uniqueDataSourceProvider == null ? null : uniqueDataSourceProvider.getIfUnique();
    if (unique != null) {
      for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
        if (entry.getValue() == unique) {
          return new ResolvedDataSource(entry.getKey(), entry.getValue());
        }
      }
      return new ResolvedDataSource("dataSource", unique);
    }

    throw new IllegalArgumentException("sql datasource is required");
  }

  private DataSource resolveDefaultDataSource() {
    if (dataSources.size() == 1) {
      return dataSources.values().iterator().next();
    }
    return uniqueDataSourceProvider == null ? null : uniqueDataSourceProvider.getIfUnique();
  }

  private DataSource createInlineJdbcDataSource(Object jdbcConnectionValue) {
    if (!(jdbcConnectionValue instanceof Map)) {
      return null;
    }
    Map<?, ?> jdbcConnection = (Map<?, ?>) jdbcConnectionValue;
    String url = stringValue(jdbcConnection.get("url"));
    if (isBlank(url)) {
      return null;
    }

    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    String driverClassName = stringValue(jdbcConnection.get("driverClassName"));
    if (!isBlank(driverClassName)) {
      dataSource.setDriverClassName(driverClassName);
    }
    dataSource.setUrl(url);
    dataSource.setUsername(stringValue(jdbcConnection.get("username")));
    dataSource.setPassword(stringValue(jdbcConnection.get("password")));
    Object propertiesValue = jdbcConnection.get("properties");
    if (propertiesValue instanceof Map) {
      Properties properties = new Properties();
      ((Map<?, ?>) propertiesValue).forEach((key, value) -> {
        if (key != null && value != null) {
          properties.setProperty(String.valueOf(key), String.valueOf(value));
        }
      });
      dataSource.setConnectionProperties(properties);
    }
    return dataSource;
  }

  @AllowDirectJDBC("metadata introspection via DatabaseMetaData API")
  private void fillMetadata(SqlDataSourceContext context) {
    DataSource dataSource = context.getDataSource();
    if (dataSource == null) {
      return;
    }
    try (Connection connection = dataSource.getConnection()) {
      DatabaseMetaData metadata = connection.getMetaData();
      if (isBlank(context.getDialect())) {
        context.setDialect(normalizeDialect(metadata.getDatabaseProductName()));
      }
      if (isBlank(context.getDialectVersion())) {
        context.setDialectVersion(metadata.getDatabaseProductVersion());
      }
    } catch (SQLException error) {
      logger.warn("Failed to retrieve database metadata for dialect detection: {}", error.getMessage());
    }
  }

  private String normalizeDialect(String dialect) {
    if (isBlank(dialect)) {
      return dialect;
    }
    String normalized = dialect.trim().toLowerCase();
    if (normalized.contains("mysql")) {
      return "mysql";
    }
    if (normalized.contains("postgresql") || normalized.contains("postgres")) {
      return "postgresql";
    }
    if (normalized.contains("mariadb")) {
      return "mariadb";
    }
    if (normalized.contains("oracle")) {
      return "oracle";
    }
    if (normalized.contains("sql server")) {
      return "sqlserver";
    }
    if (normalized.contains("h2")) {
      return "h2";
    }
    return normalized;
  }

  private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static class ResolvedDataSource {
    private final String name;
    private final DataSource dataSource;

    private ResolvedDataSource(String name, DataSource dataSource) {
      this.name = name;
      this.dataSource = dataSource;
    }

    private String getName() {
      return name;
    }

    private DataSource getDataSource() {
      return dataSource;
    }
  }
}
