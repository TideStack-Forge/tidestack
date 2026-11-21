package com.ouroboros.statistics.reporting.runtime;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.AppContext;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.runtime.provider.AppDefaultDatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.provider.AppDefaultChartDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.controller.DatasetExecutionController;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionContextResolver;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionService;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetPermissionInjector;
import com.ouroboros.statistics.reporting.runtime.provider.RuntimeOverrideDatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.provider.RuntimeOverrideChartDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.execution.SqlDataSourceProvider;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsReportingRuntimeAutoConfigurationContractTest {

  @Test
  void springFactoriesRegistersStatisticsReportingRuntimeAutoConfiguration() {
    String springFactories = readResource("META-INF/spring.factories");

    assertTrue(springFactories.contains(
        "org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.ouroboros.statistics.reporting.runtime.StatisticsReportingRuntimeAutoConfiguration"));
  }

  @Test
  void autoConfigurationCreatesExecutionChainBeans() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(loadRuntimeAutoConfigurationClass(), TestConfig.class);
      context.refresh();

      assertNotNull(context.getBean(DatasetExecutionService.class));
      assertNotNull(context.getBean(SqlDataSourceProvider.class));
      assertNotNull(context.getBean(DatasetExecutionController.class));
    } finally {
      context.close();
    }
  }

  @Test
  void autoConfigurationCreatesDefaultRuntimeDefinitionProvidersWhenAppContextPresent() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(loadRuntimeAutoConfigurationClass(), TestConfig.class);
      context.refresh();

      assertNotNull(context.getBean(AppDefaultDatasetDefinitionProvider.class));
      assertNotNull(context.getBean(AppDefaultChartDefinitionProvider.class));
      assertNotNull(context.getBean(RuntimeOverrideDatasetDefinitionProvider.class));
      assertNotNull(context.getBean(RuntimeOverrideChartDefinitionProvider.class));
    } finally {
      context.close();
    }
  }

  @Test
  void autoConfigurationShouldUseCustomPermissionInjectorForExportChecks() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(loadRuntimeAutoConfigurationClass(), CustomPermissionConfig.class);
      context.refresh();

      DatasetExecutionController controller = context.getBean(DatasetExecutionController.class);
      DatasetExecutionRequest request = new DatasetExecutionRequest();
      request.setDatasetName("demo.sales");

      BusinessException error = assertThrows(BusinessException.class, () -> controller.export(request.getDatasetName(), request));

      assertEquals("export permission denied", error.getMessage());
    } finally {
      context.close();
    }
  }

  @Test
  void autoConfigurationShouldUseCustomExecutionContextResolver() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(loadRuntimeAutoConfigurationClass(), CustomExecutionContextConfig.class);
      context.refresh();

      DatasetExecutionController controller = context.getBean(DatasetExecutionController.class);
      DatasetExecutionRequest request = new DatasetExecutionRequest();
      request.setDatasetName("demo.sales");
      request.setExecutionContext(java.util.Collections.<String, Object>singletonMap(
          "roles",
          java.util.Collections.singletonList("guest")
      ));

      WebApiResult<com.ouroboros.statistics.reporting.runtime.result.DatasetResult> response = controller.execute(request.getDatasetName(), request);

      assertEquals(0, response.getCode());
      assertEquals("trusted-role",
          ((java.util.List<?>) response.getData().getExecutionMeta().get("resolvedRoles")).get(0));
    } finally {
      context.close();
    }
  }

  @Test
  void autoConfigurationShouldResolveExecutionContextOnlyOnceForExecute() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(loadRuntimeAutoConfigurationClass(), CountingExecutionContextConfig.class);
      context.refresh();

      DatasetExecutionController controller = context.getBean(DatasetExecutionController.class);
      AtomicInteger counter = context.getBean("executionContextResolverInvocationCounter", AtomicInteger.class);

      DatasetExecutionRequest request = new DatasetExecutionRequest();
      request.setDatasetName("demo.sales");
      request.setExecutionContext(Collections.<String, Object>singletonMap(
          "roles",
          Collections.singletonList("guest")
      ));

      WebApiResult<com.ouroboros.statistics.reporting.runtime.result.DatasetResult> response = controller.execute(request.getDatasetName(), request);

      assertEquals(0, response.getCode());
      assertEquals(1, counter.get());
    } finally {
      context.close();
    }
  }

  @Test
  void autoConfigurationShouldResolveExecutionContextOnlyOnceForExport() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(loadRuntimeAutoConfigurationClass(), CountingExecutionContextConfig.class);
      context.refresh();

      DatasetExecutionController controller = context.getBean(DatasetExecutionController.class);
      AtomicInteger counter = context.getBean("executionContextResolverInvocationCounter", AtomicInteger.class);

      DatasetExecutionRequest request = new DatasetExecutionRequest();
      request.setDatasetName("demo.sales");
      request.setExecutionContext(Collections.<String, Object>singletonMap(
          "roles",
          Collections.singletonList("guest")
      ));

      WebApiResult<Map<String, Object>> response = controller.export(request.getDatasetName(), request);

      assertEquals(0, response.getCode());
      assertEquals(1, counter.get());
    } finally {
      context.close();
    }
  }

  private Class<?> loadRuntimeAutoConfigurationClass() throws Exception {
    return Class.forName("com.ouroboros.statistics.reporting.runtime.StatisticsReportingRuntimeAutoConfiguration");
  }

  private static DatasetDefinition publishedDataset() {
    DatasetDefinition dataset = new DatasetDefinition();
    dataset.setName("demo.sales");
    dataset.setIsEnabled(Boolean.TRUE);
    dataset.setSchema(schema(field("id"), field("name")));
    dataset.setQueryDefinition(sqlQuery("select 'A-1' as id, 'Phone' as name"));
    return dataset;
  }

  private static SqlQueryDefinition sqlQuery(String sql) {
    SqlQueryDefinition queryDefinition = new SqlQueryDefinition();
    queryDefinition.setSql(sql);
    return queryDefinition;
  }

  private static DatasetSchemaDefinition schema(DatasetFieldDefinition... fields) {
    DatasetSchemaDefinition schema = new DatasetSchemaDefinition();
    schema.setFields(java.util.Arrays.asList(fields));
    return schema;
  }

  private static DatasetFieldDefinition field(String name) {
    DatasetFieldDefinition field = new DatasetFieldDefinition();
    field.setName(name);
    field.setTitle(name);
    field.setDataType("string");
    field.setSemanticRole("detail");
    field.setExportable(Boolean.TRUE);
    return field;
  }

  private String readResource(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    assertNotNull(classLoader, "classLoader should exist");

    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      assertNotNull(inputStream, "resource should exist: " + resourcePath);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AssertionError("failed to read resource: " + resourcePath, e);
    }
  }

  @Configuration
  static class TestConfig {
    @Bean
    public DataSource reportingDataSource() {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:statistics_reporting_runtime;MODE=MYSQL;DB_CLOSE_DELAY=-1");
      dataSource.setUser("sa");
      dataSource.setPassword("");
      return dataSource;
    }

    @Bean
    public AppContext appContext() {
      return () -> java.nio.file.Paths.get("target", "statistics-reporting-runtime-test-app")
          .toAbsolutePath()
          .toString();
    }
  }

  @Configuration
  static class CustomPermissionConfig {
    @Bean
    public DataSource reportingDataSource() {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:statistics_reporting_runtime_custom_permission;MODE=MYSQL;DB_CLOSE_DELAY=-1");
      dataSource.setUser("sa");
      dataSource.setPassword("");
      return dataSource;
    }

    @Bean
    public AppContext appContext() {
      return () -> java.nio.file.Paths.get("target", "statistics-reporting-runtime-test-app-custom")
          .toAbsolutePath()
          .toString();
    }

    @Bean
    public DatasetDefinitionProvider datasetDefinitionProvider() {
      return () -> java.util.Collections.singletonList(publishedDataset());
    }

    @Bean
    public DatasetPermissionInjector datasetPermissionInjector() {
      return new DatasetPermissionInjector() {
        @Override
        public com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest inject(
            DatasetDefinition dataset,
            com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest request) {
          return request;
        }

        @Override
        public boolean canExport(DatasetDefinition dataset,
                                 com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest request) {
          return false;
        }
      };
    }
  }

  @Configuration
  static class CustomExecutionContextConfig {
    @Bean
    public DataSource reportingDataSource() {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:statistics_reporting_runtime_custom_context;MODE=MYSQL;DB_CLOSE_DELAY=-1");
      dataSource.setUser("sa");
      dataSource.setPassword("");
      return dataSource;
    }

    @Bean
    public AppContext appContext() {
      return () -> java.nio.file.Paths.get("target", "statistics-reporting-runtime-test-app-context")
          .toAbsolutePath()
          .toString();
    }

    @Bean
    public DatasetDefinitionProvider datasetDefinitionProvider() {
      return () -> java.util.Collections.singletonList(publishedDataset());
    }

    @Bean
    public DatasetExecutionContextResolver datasetExecutionContextResolver() {
      return request -> java.util.Collections.<String, Object>singletonMap(
          "roles",
          java.util.Collections.singletonList("trusted-role")
      );
    }

    @Bean
    public DatasetExecutionService datasetExecutionService() {
      return request -> {
        com.ouroboros.statistics.reporting.runtime.result.DatasetResult result =
            new com.ouroboros.statistics.reporting.runtime.result.DatasetResult();
        java.util.Map<String, Object> executionMeta = new java.util.LinkedHashMap<String, Object>();
        executionMeta.put("resolvedRoles", request.getExecutionContext().get("roles"));
        result.setExecutionMeta(executionMeta);
        return result;
      };
    }
  }

  @Configuration
  static class CountingExecutionContextConfig {
    @Bean
    public DataSource reportingDataSource() {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:statistics_reporting_runtime_counting_context;MODE=MYSQL;DB_CLOSE_DELAY=-1");
      dataSource.setUser("sa");
      dataSource.setPassword("");
      return dataSource;
    }

    @Bean
    public AppContext appContext() {
      return () -> java.nio.file.Paths.get("target", "statistics-reporting-runtime-test-app-counting")
          .toAbsolutePath()
          .toString();
    }

    @Bean
    public DatasetDefinitionProvider datasetDefinitionProvider() {
      return () -> java.util.Collections.singletonList(publishedDataset());
    }

    @Bean
    public AtomicInteger executionContextResolverInvocationCounter() {
      return new AtomicInteger();
    }

    @Bean
    public DatasetExecutionContextResolver datasetExecutionContextResolver(AtomicInteger executionContextResolverInvocationCounter) {
      return request -> {
        executionContextResolverInvocationCounter.incrementAndGet();
        return java.util.Collections.<String, Object>singletonMap(
            "roles",
            java.util.Collections.singletonList("trusted-role")
        );
      };
    }
  }
}
