package com.ouroboros.statistics.reporting.dev.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel;
import com.ouroboros.statistics.reporting.dev.provider.WorkspaceChartDefinitionProvider;
import com.ouroboros.statistics.reporting.dev.provider.WorkspaceDatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.dev.service.DatasetDevConfigService;
import com.ouroboros.statistics.reporting.runtime.center.DefinitionCenter;
import com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory;
import com.ouroboros.statistics.reporting.runtime.service.ChartCompiler;
import com.ouroboros.statistics.reporting.runtime.service.ChartExecutionService;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionContextResolver;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetPermissionInjector;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionService;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetParameterNormalizer;
import com.ouroboros.statistics.reporting.runtime.executor.DatasetExecutor;
import com.ouroboros.statistics.reporting.runtime.exception.StatisticsReportingExceptionTranslator;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.result.ChartExecutionResponse;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/chart")
public class ChartPreviewController {
  private final AppHomeResolver appHomeResolver;
  private final List<DatasetExecutor> datasetExecutors;
  private final DatasetParameterNormalizer parameterNormalizer;
  private final DatasetPermissionInjector datasetPermissionInjector;
  private final DatasetDevConfigService datasetDevConfigService;
  private final ChartCompiler chartCompiler;

  private com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory previewExecutorFactory;

  @Autowired
  void setPreviewExecutorFactory(com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory factory) {
    this.previewExecutorFactory = factory;
  }

  @Autowired
  public ChartPreviewController(AppHomeResolver appHomeResolver,
                                List<DatasetExecutor> datasetExecutors,
                                DatasetParameterNormalizer parameterNormalizer,
                                DatasetPermissionInjector datasetPermissionInjector,
                                ObjectProvider<DatasetDevConfigService> datasetDevConfigServiceProvider,
                                ChartCompiler chartCompiler) {
    this(appHomeResolver,
        datasetExecutors,
        parameterNormalizer,
        datasetPermissionInjector,
        datasetDevConfigServiceProvider.orderedStream()
            .findFirst()
            .orElseGet(ChartPreviewController::unavailableDatasetDevConfigService),
        chartCompiler);
  }

  public ChartPreviewController(AppHomeResolver appHomeResolver,
                                List<DatasetExecutor> datasetExecutors,
                                DatasetParameterNormalizer parameterNormalizer,
                                DatasetPermissionInjector datasetPermissionInjector,
                                DatasetDevConfigService datasetDevConfigService,
                                ChartCompiler chartCompiler) {
    this.appHomeResolver = Objects.requireNonNull(appHomeResolver, "appHomeResolver is required");
    this.datasetExecutors = Collections.unmodifiableList(new ArrayList<DatasetExecutor>(
        Objects.requireNonNull(datasetExecutors, "datasetExecutors is required")));
    this.parameterNormalizer = Objects.requireNonNull(parameterNormalizer, "parameterNormalizer is required");
    this.datasetPermissionInjector = Objects.requireNonNull(
        datasetPermissionInjector, "datasetPermissionInjector is required");
    this.datasetDevConfigService = Objects.requireNonNull(
        datasetDevConfigService, "datasetDevConfigService is required");
    this.chartCompiler = Objects.requireNonNull(chartCompiler, "chartCompiler is required");
  }

  @PostMapping("/preview")
  public WebApiResult<ChartExecutionResponse> preview(@PathVariable("appName") String appName,
                                                       @RequestBody ChartPreviewRequest request) {
    try {
      if (request == null || request.getChart() == null) {
        throw new IllegalArgumentException("chart definition is required");
      }
      return executePreview(appName, request, request.getChart());
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics chart preview failed");
    }
  }

  @PostMapping("/{chartName:.+}/preview")
  public WebApiResult<ChartExecutionResponse> previewSavedChart(@PathVariable("appName") String appName,
                                                                 @PathVariable("chartName") String chartName,
                                                                 @RequestBody(required = false)
                                                                 ChartPreviewRequest request) {
    try {
      Path appHome = Paths.get(appHomeResolver.getAppHomeByAppId(appName));
      ChartDefinition chart = new WorkspaceChartDefinitionProvider(appHome, false).getDefinitions().stream()
          .filter(candidate -> chartName.equalsIgnoreCase(candidate.getName()))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("chart definition not found: " + chartName));
      ChartPreviewRequest previewRequest = request == null ? new ChartPreviewRequest() : request;
      previewRequest.setChart(chart);
      return executePreview(appName, previewRequest, chart);
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics saved chart preview failed");
    }
  }

  private WebApiResult<ChartExecutionResponse> executePreview(String appName,
                                                               ChartPreviewRequest request,
                                                               ChartDefinition chart) {
    Path appHome = Paths.get(appHomeResolver.getAppHomeByAppId(appName));
    DatasetExecutionRequest executionRequest = request.copy();
    if (isBlank(executionRequest.getDatasetName())) {
      executionRequest.setDatasetName(chart.getDatasetName());
    }
    DefinitionCenter<DatasetDefinition> datasetCenter = DatasetPreviewExecutorFactory.createDatasetCenter(
        List.<DatasetDefinitionProvider>of(new WorkspaceDatasetDefinitionProvider(appHome, false)));
    applyDatasetDevConfig(appName, executionRequest, datasetCenter);
    DatasetExecutionContextResolver contextResolver = previewRequest -> previewRequest == null
        ? Collections.<String, Object>emptyMap()
        : previewRequest.getExecutionContext();
    DatasetExecutionService datasetExecutionService = DatasetPreviewExecutorFactory.createExecutionService(
        datasetCenter,
        parameterNormalizer,
        datasetPermissionInjector,
        previewExecutors(appName, request.getTestConnection(), executionRequest),
        contextResolver);
    ChartExecutionService chartExecutionService = DatasetPreviewExecutorFactory.createChartExecutionService(
        datasetCenter, datasetExecutionService, chartCompiler);
    return WebApiResult.success(chartExecutionService.execute(chart, executionRequest));
  }

  private void applyDatasetDevConfig(String appName,
                                     DatasetExecutionRequest request,
                                     DefinitionCenter<DatasetDefinition> datasetCenter) {
    if (request == null || isBlank(request.getDatasetName())) {
      return;
    }
    DatasetDevConfigModel config = datasetDevConfigService
        .findByDataset(appName, request.getDatasetName())
        .orElse(null);
    if (config == null) {
      return;
    }
    Map<String, Object> executionContext = request.getExecutionContext() == null
        ? new LinkedHashMap<String, Object>()
        : new LinkedHashMap<String, Object>(request.getExecutionContext());
    String requestedDatasource = stringValue(executionContext.get("datasource"));
    if (isBlank(requestedDatasource)) {
      requestedDatasource = resolveDatasetDatasource(datasetCenter, request.getDatasetName());
    }
    if (!sameDatasource(requestedDatasource, config.getDatasource())) {
      return;
    }
    if (!isBlank(config.getDatasource()) && isBlank(stringValue(executionContext.get("datasource")))) {
      executionContext.put("datasource", config.getDatasource());
    }
    if (config.getTestConnection() != null) {
      executionContext.put("testConnection", config.getTestConnection());
    }
    request.setExecutionContext(executionContext);

    Map<String, Object> runtimeParameters = new LinkedHashMap<String, Object>();
    if (config.getParameters() != null) {
      runtimeParameters.putAll(config.getParameters());
    }
    if (request.getRuntimeParameters() != null) {
      runtimeParameters.putAll(request.getRuntimeParameters());
    }
    request.setRuntimeParameters(runtimeParameters);
  }

  private static DatasetDevConfigService unavailableDatasetDevConfigService() {
    return new DatasetDevConfigService() {
      @Override
      public io.vavr.control.Try<DatasetDevConfigModel> save(DatasetDevConfigModel config) {
        return io.vavr.control.Try.failure(
            new UnsupportedOperationException("dataset dev config service is not configured"));
      }

      @Override
      public java.util.Optional<DatasetDevConfigModel> findByDataset(String appName, String datasetName) {
        return java.util.Optional.empty();
      }

      @Override
      public List<DatasetDevConfigModel> findByAppName(String appName) {
        return Collections.emptyList();
      }
    };
  }

  private String resolveDatasetDatasource(DefinitionCenter<DatasetDefinition> datasetCenter, String datasetName) {
    DatasetDefinition dataset = datasetCenter.getDefinition(datasetName).orElse(null);
    if (dataset != null && dataset.getQueryDefinition() instanceof SqlQueryDefinition sqlQuery) {
      return sqlQuery.getDatasource();
    }
    return null;
  }

  private boolean sameDatasource(String left, String right) {
    String normalizedLeft = isBlank(left) ? "default" : left.trim();
    String normalizedRight = isBlank(right) ? "default" : right.trim();
    return normalizedLeft.equalsIgnoreCase(normalizedRight);
  }

  private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private List<DatasetExecutor> previewExecutors(String appName,
      com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection explicit,
      DatasetExecutionRequest request) {
    if (previewExecutorFactory == null) {
      if (datasetExecutors.stream().anyMatch(DatasetExecutor::requiresDataSource)) {
        throw new com.ouroboros.exception.InternalException("dataset preview connection service is not configured");
      }
      return datasetExecutors;
    }
    Object saved = request.getExecutionContext() == null ? null : request.getExecutionContext().get("testConnection");
    var selected = explicit != null ? explicit
        : saved instanceof com.ouroboros.statistics.reporting.dev.model.DatasetTestConnection connection ? connection : null;
    return previewExecutorFactory.forApplication(appName, selected, datasetExecutors);
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
