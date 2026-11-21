package com.ouroboros.statistics.reporting.dev.service;

import java.util.List;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.center.DefaultChartDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.center.DefinitionCenter;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.runtime.service.ChartExecutionService;
import com.ouroboros.statistics.reporting.runtime.service.ChartCompiler;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionContextResolver;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionService;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetParameterNormalizer;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetPermissionInjector;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultDatasetExecutionService;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.statistics.reporting.dev.model.DatasetDataSourceRequest;
import com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultSqlDialectResolver;
import com.ouroboros.statistics.reporting.runtime.execution.SpringSqlDataSourceProvider;
import com.ouroboros.statistics.reporting.runtime.execution.SqlDataSourceProvider;
import com.ouroboros.statistics.reporting.runtime.execution.SqlExecutionBackend;
import com.ouroboros.statistics.reporting.runtime.executor.DatasetExecutor;
import com.ouroboros.statistics.reporting.runtime.executor.SqlDatasetExecutor;

/** Reuses the SQL execution pipeline with connections resolved for the edited application. */
@Component
public class DatasetPreviewExecutorFactory {
  private final DatasetDesignDataSourceService dataSources;
  private final DefaultSqlDialectResolver dialectResolver;
  private final SqlExecutionBackend backend;

  public DatasetPreviewExecutorFactory(DatasetDesignDataSourceService dataSources,
                                       DefaultSqlDialectResolver dialectResolver,
                                       SqlExecutionBackend backend) {
    this.dataSources = dataSources;
    this.dialectResolver = dialectResolver;
    this.backend = backend;
  }

  public List<DatasetExecutor> forApplication(String appName, DatasetTestConnection explicit,
      com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest request, List<DatasetExecutor> executors) {
    Object saved = request.getExecutionContext() == null ? null : request.getExecutionContext().get("testConnection");
    DatasetTestConnection selected = explicit != null ? explicit
        : saved instanceof DatasetTestConnection connection ? connection : null;
    return forApplication(appName, selected, executors);
  }

  public List<DatasetExecutor> forApplication(String appName, DatasetTestConnection selected,
                                             List<DatasetExecutor> executors) {
    SqlDataSourceProvider provider = (query, request) -> {
      String logicalName = DatasetDesignDataSourceService.sourceName(query.getDatasource());
      DatasetDataSourceRequest connectionRequest = new DatasetDataSourceRequest();
      connectionRequest.setDatasource(logicalName);
      connectionRequest.setTestConnection(selected);
      var definition = dataSources.connection(appName, connectionRequest)
          .orElseThrow(() -> new BusinessException("datasource-required: 请展开预览连接，选择实际连接或运行容器"));
      if (!"jdbc".equalsIgnoreCase(definition.getType())) {
        throw new BusinessException("datasource-unreachable: 查询预览仅支持 JDBC 连接");
      }
      var properties = definition.getProperties();
      var source = new DriverManagerDataSource();
      source.setDriverClassName((String) properties.get("driverClassName"));
      source.setUrl((String) properties.get("url"));
      source.setUsername((String) properties.get("username"));
      source.setPassword((String) properties.get("password"));
      var springProvider = new SpringSqlDataSourceProvider(Map.of(logicalName, source), null);
      var resolvedRequest = request.copy();
      var executionContext = new LinkedHashMap<>(request.getExecutionContext());
      executionContext.put("datasource", logicalName);
      resolvedRequest.setExecutionContext(executionContext);
      return springProvider.resolve(query, resolvedRequest);
    };
    return executors.stream().map(executor -> executor instanceof SqlDatasetExecutor
        ? (DatasetExecutor) new SqlDatasetExecutor(dialectResolver, provider, backend) : executor).toList();
  }
  /** Creates isolated draft centers for preview execution. */
  public static DefinitionCenter<DatasetDefinition> createDatasetCenter(
      List<DatasetDefinitionProvider> datasets) {
    return new DefaultDatasetDefinitionCenter(datasets, false);
  }

  public static DatasetExecutionService createExecutionService(
      DefinitionCenter<DatasetDefinition> datasets,
      DatasetParameterNormalizer normalizer, DatasetPermissionInjector permissions,
      List<DatasetExecutor> executors, DatasetExecutionContextResolver contextResolver) {
    return new DefaultDatasetExecutionService(datasets,
        normalizer, permissions, executors, contextResolver);
  }
  public static ChartExecutionService createChartExecutionService(
      DefinitionCenter<DatasetDefinition> datasets,
      DatasetExecutionService executionService, ChartCompiler compiler) {
    return new ChartExecutionService(datasets,
        new DefaultChartDefinitionCenter(List.of(), false),
        executionService, compiler);
  }

}
