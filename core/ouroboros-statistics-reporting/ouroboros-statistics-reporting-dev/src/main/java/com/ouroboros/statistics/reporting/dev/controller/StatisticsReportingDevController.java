package com.ouroboros.statistics.reporting.dev.controller;

import com.ouroboros.statistics.reporting.dev.model.DatasetInferSchemaRequest;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefinitionCenter;
import com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionContextResolver;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetPermissionInjector;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetParameterNormalizer;
import com.ouroboros.statistics.reporting.runtime.exception.StatisticsReportingExceptionTranslator;
import com.ouroboros.statistics.reporting.runtime.executor.DatasetExecutor;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.statistics.reporting.dev.model.governance.AssetReferenceGraph;
import com.ouroboros.statistics.reporting.dev.provider.WorkspaceDatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.dev.service.AssetReferenceGraphService;
import com.ouroboros.statistics.reporting.dev.service.DatasetSchemaInferenceService;
import com.ouroboros.statistics.reporting.dev.model.editor.DatasetInferSchemaResult;
import com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel;
import com.ouroboros.statistics.reporting.dev.service.DatasetDevConfigService;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/dataset")
public class StatisticsReportingDevController {
  private final AppHomeResolver appHomeResolver;
  private final List<DatasetExecutor> datasetExecutors;
  private final DatasetParameterNormalizer parameterNormalizer;
  private final DatasetPermissionInjector datasetPermissionInjector;
  private final DatasetSchemaInferenceService datasetSchemaInferenceService;
  private final DatasetDevConfigService datasetDevConfigService;
  private final AssetReferenceGraphService assetReferenceGraphService;

  private com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory previewExecutorFactory;

  @Autowired
  void setPreviewExecutorFactory(com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory factory) {
    this.previewExecutorFactory = factory;
  }

  @Autowired
  public StatisticsReportingDevController(AppHomeResolver appHomeResolver,
                                          List<DatasetExecutor> datasetExecutors,
                                          DatasetParameterNormalizer parameterNormalizer,
                                          DatasetPermissionInjector datasetPermissionInjector,
                                          ObjectProvider<DatasetSchemaInferenceService> datasetSchemaInferenceServiceProvider,
                                          ObjectProvider<DatasetDevConfigService> datasetDevConfigServiceProvider,
                                          ObjectProvider<AssetReferenceGraphService> assetReferenceGraphServiceProvider) {
    this(appHomeResolver,
        datasetExecutors,
        parameterNormalizer,
        datasetPermissionInjector,
        datasetSchemaInferenceServiceProvider.getIfAvailable(
            StatisticsReportingDevController::unavailableDatasetSchemaInferenceService),
        datasetDevConfigServiceProvider.orderedStream()
            .findFirst()
            .orElseGet(StatisticsReportingDevController::unavailableDatasetDevConfigService),
        assetReferenceGraphServiceProvider.getIfAvailable(
            StatisticsReportingDevController::unavailableAssetReferenceGraphService));
  }

  public StatisticsReportingDevController(AppHomeResolver appHomeResolver,
                                          List<DatasetExecutor> datasetExecutors,
                                          DatasetParameterNormalizer parameterNormalizer,
                                          DatasetPermissionInjector datasetPermissionInjector,
                                          DatasetSchemaInferenceService datasetSchemaInferenceService) {
    this(appHomeResolver,
        datasetExecutors,
        parameterNormalizer,
        datasetPermissionInjector,
        datasetSchemaInferenceService,
        unavailableDatasetDevConfigService(),
        unavailableAssetReferenceGraphService());
  }

  public StatisticsReportingDevController(AppHomeResolver appHomeResolver,
                                          List<DatasetExecutor> datasetExecutors,
                                          DatasetParameterNormalizer parameterNormalizer,
                                          DatasetPermissionInjector datasetPermissionInjector,
                                          DatasetSchemaInferenceService datasetSchemaInferenceService,
                                          DatasetDevConfigService datasetDevConfigService) {
    this(appHomeResolver,
        datasetExecutors,
        parameterNormalizer,
        datasetPermissionInjector,
        datasetSchemaInferenceService,
        datasetDevConfigService,
        unavailableAssetReferenceGraphService());
  }

  public StatisticsReportingDevController(AppHomeResolver appHomeResolver,
                                          List<DatasetExecutor> datasetExecutors,
                                          DatasetParameterNormalizer parameterNormalizer,
                                          DatasetPermissionInjector datasetPermissionInjector,
                                          DatasetSchemaInferenceService datasetSchemaInferenceService,
                                          DatasetDevConfigService datasetDevConfigService,
                                          AssetReferenceGraphService assetReferenceGraphService) {
    this.appHomeResolver = Objects.requireNonNull(appHomeResolver, "appHomeResolver is required");
    this.datasetExecutors = Collections.unmodifiableList(new ArrayList<DatasetExecutor>(
        Objects.requireNonNull(datasetExecutors, "datasetExecutors is required")));
    this.parameterNormalizer = Objects.requireNonNull(parameterNormalizer, "parameterNormalizer is required");
    this.datasetPermissionInjector =
        Objects.requireNonNull(datasetPermissionInjector, "datasetPermissionInjector is required");
    this.datasetSchemaInferenceService =
        Objects.requireNonNull(datasetSchemaInferenceService, "datasetSchemaInferenceService is required");
    this.datasetDevConfigService =
        Objects.requireNonNull(datasetDevConfigService, "datasetDevConfigService is required");
    this.assetReferenceGraphService =
        Objects.requireNonNull(assetReferenceGraphService, "assetReferenceGraphService is required");
  }

  public StatisticsReportingDevController(AppHomeResolver appHomeResolver,
                                          List<DatasetExecutor> datasetExecutors,
                                          DatasetParameterNormalizer parameterNormalizer,
                                          DatasetPermissionInjector datasetPermissionInjector) {
    this(appHomeResolver,
        datasetExecutors,
        parameterNormalizer,
        datasetPermissionInjector,
        unavailableDatasetSchemaInferenceService(),
        unavailableDatasetDevConfigService(),
        unavailableAssetReferenceGraphService());
  }

  private static DatasetSchemaInferenceService unavailableDatasetSchemaInferenceService() {
    return (appName, request) -> {
      throw new UnsupportedOperationException("dataset schema inference service is not configured");
    };
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

  private static AssetReferenceGraphService unavailableAssetReferenceGraphService() {
    return appName -> new AssetReferenceGraph();
  }

  @PostMapping("/infer-schema")
  public WebApiResult<DatasetInferSchemaResult> inferSchema(@PathVariable("appName") String appName,
                                                            @RequestBody DatasetInferSchemaRequest request) {
    try {
      return WebApiResult.ok(datasetSchemaInferenceService.inferSchema(
          appName,
          applyDatasetDevConfig(appName, request)
      ));
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics dataset schema inference failed");
    }
  }

  @PostMapping("/test-connection")
  public WebApiResult<Void> testConnection(@PathVariable("appName") String appName,
                                           @RequestBody DatasetInferSchemaRequest request) {
    try {
      datasetSchemaInferenceService.testConnection(appName, request);
      return WebApiResult.ok();
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics dataset connection test failed");
    }
  }

  @PostMapping("/preview")
  public WebApiResult<DatasetResult> preview(@PathVariable("appName") String appName,
                                             @RequestBody DatasetPreviewRequest request) {
    Path appHome = Paths.get(appHomeResolver.getAppHomeByAppId(appName));
    DatasetExecutionRequest executionRequest = request.copy();
    if (isBlank(executionRequest.getDatasetName()) && request.getDataset() != null && !isBlank(request.getDataset().getName())) {
      executionRequest.setDatasetName(request.getDataset().getName());
    }
    var datasetCenter = DatasetPreviewExecutorFactory.createDatasetCenter(createDatasetProviders(appHome, request));
    applyPreviewDialect(request, executionRequest);
    applyDatasetDevConfig(appName, executionRequest, datasetCenter);
    DatasetExecutionContextResolver executionContextResolver = passthroughExecutionContextResolver();
    var executionService = DatasetPreviewExecutorFactory.createExecutionService(
        datasetCenter,
        parameterNormalizer,
        datasetPermissionInjector,
        previewExecutors(appName, request.getTestConnection(), executionRequest),
        executionContextResolver
    );
    try {
      return WebApiResult.success(executionService.execute(executionRequest));
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics dataset preview failed");
    }
  }

  private void applyPreviewDialect(DatasetPreviewRequest request,
                                    DatasetExecutionRequest executionRequest) {
    if (request == null || isBlank(request.getDialectName())) {
      return;
    }
    Map<String, Object> executionContext = executionRequest.getExecutionContext() == null
        ? new LinkedHashMap<String, Object>()
        : new LinkedHashMap<String, Object>(executionRequest.getExecutionContext());
    executionContext.put("dialect", request.getDialectName());
    executionRequest.setExecutionContext(executionContext);
  }

  @GetMapping("/asset-reference-graph")
  public WebApiResult<AssetReferenceGraph> assetReferenceGraph(@PathVariable("appName") String appName) {
    return WebApiResult.ok(assetReferenceGraphService.resolve(appName));
  }

  @GetMapping("/{datasetName:.+}/dev-config")
  public WebApiResult<DatasetDevConfigModel> getDatasetDevConfig(@PathVariable("appName") String appName,
                                                                   @PathVariable("datasetName") String datasetName) {
    DatasetDevConfigModel config = datasetDevConfigService
        .findByDataset(appName, datasetName)
        .orElseGet(() -> newDatasetDevConfig(appName, datasetName));
    return WebApiResult.ok(config);
  }

  @PutMapping("/{datasetName:.+}/dev-config")
  public WebApiResult<DatasetDevConfigModel> saveDatasetDevConfig(@PathVariable("appName") String appName,
                                                                    @PathVariable("datasetName") String datasetName,
                                                                    @RequestBody DatasetDevConfigModel config) {
    DatasetDevConfigModel candidate = config == null ? new DatasetDevConfigModel() : config;
    candidate.setAppName(appName);
    candidate.setDatasetName(datasetName);
    return WebApiResult.of(datasetDevConfigService.save(candidate));
  }

  private DatasetDevConfigModel newDatasetDevConfig(String appName, String datasetName) {
    DatasetDevConfigModel config = new DatasetDevConfigModel();
    config.setAppName(appName);
    config.setDatasetName(datasetName);
    return config;
  }

  private DatasetInferSchemaRequest applyDatasetDevConfig(String appName, DatasetInferSchemaRequest request) {
    if (request == null || isBlank(request.getDatasetName())) {
      return request;
    }
    DatasetDevConfigModel config = datasetDevConfigService
        .findByDataset(appName, request.getDatasetName())
        .orElse(null);
    if (config == null) {
      return request;
    }
    if (isBlank(request.getDatasource()) && !isBlank(config.getDatasource())) {
      request.setDatasource(config.getDatasource());
    }
    if (!sameDatasource(request.getDatasource(), config.getDatasource())) {
      return request;
    }
    if (request.getTestConnection() == null) {
      request.setTestConnection(config.getTestConnection());
    }
    Map<String, Object> parameters = new LinkedHashMap<String, Object>();
    if (config.getParameters() != null) {
      parameters.putAll(config.getParameters());
    }
    if (request.getParameters() != null) {
      parameters.putAll(request.getParameters());
    }
    request.setParameters(parameters);
    return request;
  }

  private void applyDatasetDevConfig(String appName,
                                     DatasetExecutionRequest executionRequest,
                                     DefinitionCenter<DatasetDefinition> datasetCenter) {
    datasetDevConfigService.applyToExecutionRequest(appName, executionRequest,
        executionRequest == null ? null : resolveDatasetDatasource(datasetCenter, executionRequest.getDatasetName()));
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

  private DatasetExecutionContextResolver passthroughExecutionContextResolver() {
    return request -> request == null ? Collections.<String, Object>emptyMap() : request.getExecutionContext();
  }

  private List<DatasetDefinitionProvider> createDatasetProviders(Path appHome, DatasetPreviewRequest request) {
    List<DatasetDefinitionProvider> providers = new ArrayList<>();
    if (request.getDataset() != null && !isBlank(request.getDataset().getName())) {
      providers.add(new DatasetDefinitionProvider() {
        @Override
        public List<com.ouroboros.statistics.reporting.definition.DatasetDefinition> getDefinitions() {
          return Collections.singletonList(request.getDataset());
        }

        @Override
        public int getOrder() {
          return 1000;
        }
      });
    }
    providers.add(new WorkspaceDatasetDefinitionProvider(appHome, false));
    return providers;
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
    return previewExecutorFactory.forApplication(appName, explicit, request, datasetExecutors);
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

}
