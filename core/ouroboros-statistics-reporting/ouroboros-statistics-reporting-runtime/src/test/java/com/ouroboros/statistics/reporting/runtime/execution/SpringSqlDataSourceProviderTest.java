package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SpringSqlDataSourceProviderTest {

  @Test
  void shouldResolveDefaultDatasourceToTheOnlySpringDatasource() {
    DataSource primaryDataSource = h2DataSource();
    Map<String, DataSource> dataSources = new LinkedHashMap<String, DataSource>();
    dataSources.put("dataSource", primaryDataSource);

    SpringSqlDataSourceProvider provider = new SpringSqlDataSourceProvider(dataSources, null);
    SqlQueryDefinition queryDefinition = new SqlQueryDefinition();
    queryDefinition.setDatasource("default");
    DatasetExecutionRequest request = new DatasetExecutionRequest();
    request.setExecutionContext(Collections.<String, Object>emptyMap());

    SqlDataSourceContext context = provider.resolve(queryDefinition, request);

    assertEquals("default", context.getDatasource());
    assertSame(primaryDataSource, context.getDataSource());
  }

  private static DataSource h2DataSource() {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:default-datasource-provider");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    return dataSource;
  }
}
