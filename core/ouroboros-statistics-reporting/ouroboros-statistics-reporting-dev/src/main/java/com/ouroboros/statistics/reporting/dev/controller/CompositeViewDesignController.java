package com.ouroboros.statistics.reporting.dev.controller;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.businessmodel.schema.CompositeViewWebApis;
import com.ouroboros.statistics.reporting.definition.EmbeddedQueryDefinition;
import com.ouroboros.statistics.reporting.dev.service.impl.DatasetDevService;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionPolicyProvider;
import com.ouroboros.web.WebApiResult;

/** Supplies development contracts without executing referenced business actions. */
@RestController
@RequestMapping("/api/dev/{appName}/composite-view")
public class CompositeViewDesignController {
  private final DatasetDevService datasets;
  private final com.ouroboros.statistics.reporting.dev.service.DatasetDevConfigService devConfig;
  private final ObjectProvider<BusinessModelProvider> models;
  private final com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory factory;
  private final List<com.ouroboros.statistics.reporting.runtime.executor.DatasetExecutor> executors;
  private final com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionContextResolver context;
  private final com.ouroboros.statistics.reporting.runtime.execution.DatasetParameterNormalizer normalizer;
  private final com.ouroboros.statistics.reporting.runtime.execution.DatasetPermissionInjector permissions;

  public CompositeViewDesignController(DatasetDevService datasets,
      com.ouroboros.statistics.reporting.dev.service.DatasetDevConfigService devConfig, ObjectProvider<BusinessModelProvider> models,
      com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory factory,
      List<com.ouroboros.statistics.reporting.runtime.executor.DatasetExecutor> executors,
      com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionContextResolver context,
      com.ouroboros.statistics.reporting.runtime.execution.DatasetParameterNormalizer normalizer,
      com.ouroboros.statistics.reporting.runtime.execution.DatasetPermissionInjector permissions) {
    this.datasets = datasets;
    this.devConfig = devConfig;
    this.models = models;
    this.factory = factory;
    this.executors = executors;
    this.context = context;
    this.normalizer = normalizer;
    this.permissions = permissions;
  }

  @GetMapping("/execution-policy")
  public WebApiResult<Object> policy() {
    return WebApiResult.success(DatasetExecutionPolicyProvider.current());
  }

  @PostMapping("/validate")
  public WebApiResult<Map<String, List<String>>> validate(@RequestBody CompositeViewBusinessModelMeta definition) {
    return WebApiResult.success(Map.of("diagnostics", diagnostics(definition)));
  }

  private List<String> diagnostics(CompositeViewBusinessModelMeta definition) {
    BusinessModelProvider provider = models.getIfUnique();
    if (provider == null) throw new com.ouroboros.exception.PlatformException("业务模型开发服务不可用");
    var model = com.ouroboros.util.SPI.getCachedServiceStream(com.ouroboros.businessmodel.BusinessModelBuilder.class)
        .filter(builder -> Boolean.TRUE.equals(builder.support(definition))).findFirst()
        .map(builder -> (com.ouroboros.businessmodel.BusinessModel) builder.build(definition, provider))
        .orElseThrow(() -> new com.ouroboros.exception.PlatformException("未注册综合查询模型构建器"));
    return java.util.stream.Stream.concat(model.getValidationResult().getErrors().stream(),
        model.getRelationValidationResult().getErrors().stream()).map(Throwable::getMessage).toList();
  }

  public record Preview(CompositeViewBusinessModelMeta definition, DatasetPreviewRequest request) { }

  @PostMapping("/preview")
  public WebApiResult<com.ouroboros.statistics.reporting.runtime.result.DatasetResult> preview(
      @PathVariable String appName, @RequestBody Preview preview) {
    var definition = preview.definition();
    var errors = diagnostics(definition);
    if (!errors.isEmpty()) throw new com.ouroboros.exception.BusinessException(String.join("；", errors));
    var source = definition.getDatasetName() == null || definition.getDatasetName().isBlank()
        ? EmbeddedQueryDefinition.resolve(definition.getEmbeddedQuery(), definition.getFullName())
        : datasets.getOne(definition.getDatasetName()).orElseThrow();
    var request = preview.request() == null ? new DatasetPreviewRequest() : preview.request().copy();
    request.setDatasetName(source.getName());
    String datasource = source.getQueryDefinition() instanceof com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition sql
        ? sql.getDatasource() : null;
    devConfig.applyToExecutionRequest(appName, request, datasource);
    var center = com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory.createDatasetCenter(
        List.of(() -> List.of(source)));
    var execution = com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory.createExecutionService(
        center, normalizer, permissions, factory.forApplication(appName, request.getTestConnection(), request, executors), context);
    var service = new com.ouroboros.statistics.reporting.runtime.service.CompositeViewQueryService(execution, context);
    return WebApiResult.success(service.execute(definition, request));
  }

  @GetMapping("/contracts")
  public WebApiResult<Map<String, Object>> contracts() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("datasets", datasets.search(Map.of()));
    BusinessModelProvider provider = models.getIfUnique();
    result.put("apis", provider == null ? List.of() : CompositeViewWebApis.available(provider).values());
    result.put("executionPolicy", DatasetExecutionPolicyProvider.current());
    return WebApiResult.success(result);
  }
}
