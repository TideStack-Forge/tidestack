package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.executor.DatasetExecutor;
import com.ouroboros.statistics.reporting.runtime.result.DatasetDiagnostics;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;

public class DefaultDatasetExecutionService implements DatasetExecutionService {
  private final DefinitionCenter<DatasetDefinition> datasetDefinitionCenter;
  private final DatasetParameterNormalizer parameterNormalizer;
  private final DatasetPermissionInjector datasetPermissionInjector;
  private final DynamicFilterCompiler dynamicFilterCompiler = new DynamicFilterCompiler();
  private final DatasetExecutionContextResolver executionContextResolver;
  private final List<DatasetExecutor> datasetExecutors;

  public DefaultDatasetExecutionService(DefinitionCenter<DatasetDefinition> datasetDefinitionCenter,
                                        DatasetParameterNormalizer parameterNormalizer,
                                        DatasetPermissionInjector datasetPermissionInjector,
                                        List<DatasetExecutor> datasetExecutors,
                                        DatasetExecutionContextResolver executionContextResolver) {
    this.datasetDefinitionCenter = Objects.requireNonNull(datasetDefinitionCenter, "datasetDefinitionCenter is required");
    this.parameterNormalizer = Objects.requireNonNull(parameterNormalizer, "parameterNormalizer is required");
    this.datasetPermissionInjector =
        Objects.requireNonNull(datasetPermissionInjector, "datasetPermissionInjector is required");
    this.executionContextResolver =
        Objects.requireNonNull(executionContextResolver, "executionContextResolver is required");
    this.datasetExecutors = Collections.unmodifiableList(new ArrayList<DatasetExecutor>(
        Objects.requireNonNull(datasetExecutors, "datasetExecutors is required")));
  }

  @Override
  public DatasetResult execute(DatasetExecutionRequest request) {
    DatasetExecutionRequest trustedRequest = resolveRequest(request);
    return executeLoaded(loadDefinitions(trustedRequest), trustedRequest);
  }

  @Override
  public DatasetResult execute(DatasetDefinition definition, DatasetExecutionRequest request) {
    new com.ouroboros.statistics.reporting.validation.DatasetDefinitionValidator().validate(definition, false);
    return executeLoaded(new LoadedDefinitions(definition), resolveRequest(request));
  }

  private DatasetResult executeLoaded(LoadedDefinitions definitions, DatasetExecutionRequest trustedRequest) {
    var policy = DatasetExecutionPolicyProvider.current().tighten(definitions.getDataset().getExecutionConstraints(), false);
    trustedRequest.setExecutionBudget(new DatasetExecutionBudget(policy, trustedRequest.isExport()));
    if (trustedRequest.isExport()) trustedRequest.setPage(null);
    DatasetExecutionRequest normalizedRequest = normalizeRequest(definitions, trustedRequest);
    DatasetExecutionRequest authorizedRequest = authorizeRequest(definitions, normalizedRequest);
    DatasetExecutionRequest compiledRequest = compileDynamicFilter(definitions, authorizedRequest);
    DatasetResult executedResult = executeDataset(definitions, compiledRequest);
    compiledRequest.getExecutionBudget().remainingSeconds();
    if (executedResult != null) compiledRequest.getExecutionBudget().checkRows(executedResult.getRows().size());
    return projectResult(definitions, compiledRequest, executedResult);
  }

  protected DatasetExecutionRequest resolveRequest(DatasetExecutionRequest request) {
    if (request instanceof TrustedDatasetExecutionRequest) {
      return request.copy();
    }
    Map<String, Object> resolvedContext = executionContextResolver.resolve(request);
    return TrustedDatasetExecutionRequest.trust(request, resolvedContext);
  }

  protected LoadedDefinitions loadDefinitions(DatasetExecutionRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("dataset execution request is required");
    }
    if (isBlank(request.getDatasetName())) {
      throw new IllegalArgumentException("datasetName is required");
    }
    DatasetDefinition dataset = datasetDefinitionCenter.getDefinition(request.getDatasetName())
        .orElseThrow(() -> new IllegalArgumentException("dataset definition not found: " + request.getDatasetName()));

    return new LoadedDefinitions(dataset);
  }

  protected DatasetExecutionRequest normalizeRequest(LoadedDefinitions definitions, DatasetExecutionRequest request) {
    return parameterNormalizer.normalize(definitions.getDataset(), request);
  }

  protected DatasetExecutionRequest authorizeRequest(LoadedDefinitions definitions, DatasetExecutionRequest request) {
    return datasetPermissionInjector.inject(definitions.getDataset(), request);
  }

  protected DatasetExecutionRequest compileDynamicFilter(LoadedDefinitions definitions, DatasetExecutionRequest request) {
    return dynamicFilterCompiler.compile(definitions.getDataset(), request);
  }

  protected DatasetResult executeDataset(LoadedDefinitions definitions, DatasetExecutionRequest request) {
    for (DatasetExecutor datasetExecutor : datasetExecutors) {
      if (datasetExecutor != null && datasetExecutor.supports(definitions.getDataset())) {
        return datasetExecutor.execute(definitions.getDataset(), request);
      }
    }
    throw new UnsupportedOperationException("No dataset executor registered");
  }

  protected DatasetResult projectResult(LoadedDefinitions definitions,
                                        DatasetExecutionRequest request,
                                        DatasetResult result) {
    DatasetResult projectedResult = result == null ? new DatasetResult() : result;
    projectedResult.setAppliedParameters(request.getAppliedParameters());
    DatasetDiagnostics diagnostics = projectedResult.getDiagnostics() == null
        ? new DatasetDiagnostics()
        : projectedResult.getDiagnostics();
    diagnostics.getAttributes().put("normalizedParameters", copyMap(request.getAppliedParameters()));
    diagnostics.getAttributes().put("omittedOptionalParameters",
        copyList(request.getExecutionContext(), DefaultDatasetParameterNormalizer.OMITTED_PARAMETERS_CONTEXT_KEY));
    diagnostics.getAttributes().put("permissionBindings",
        copyList(request.getExecutionContext(), DefaultDatasetPermissionInjector.PERMISSION_BINDINGS_CONTEXT_KEY));
    projectedResult.setDiagnostics(diagnostics);
    projectedResult.setSummary(defaultMap(projectedResult.getSummary()));
    projectedResult.setPageInfo(defaultMap(projectedResult.getPageInfo()));
    Map<String, Object> executionMeta = defaultMap(projectedResult.getExecutionMeta());
    executionMeta.put("datasetName", definitions.getDataset().getName());
    executionMeta.put("executionPolicy", request.getExecutionBudget().policy());
    executionMeta.put("queryType", diagnostics.getQueryType());
    projectedResult.setExecutionMeta(executionMeta);
    return projectedResult;
  }

  private Map<String, Object> defaultMap(Map<String, Object> source) {
    return source == null ? new LinkedHashMap<String, Object>() : source;
  }

  private Map<String, Object> copyMap(Map<String, Object> source) {
    Map<String, Object> copied = new LinkedHashMap<String, Object>();
    if (source != null) {
      copied.putAll(source);
    }
    return copied;
  }

  private List<Object> copyList(Map<String, Object> executionContext, String key) {
    Object value = executionContext == null ? null : executionContext.get(key);
    if (!(value instanceof List)) {
      return new ArrayList<Object>();
    }
    List<?> values = (List<?>) value;
    return new ArrayList<Object>(values);
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  protected static class LoadedDefinitions {
    private final DatasetDefinition dataset;

    protected LoadedDefinitions(DatasetDefinition dataset) {
      this.dataset = dataset;
    }

    protected DatasetDefinition getDataset() {
      return dataset;
    }
  }
}
