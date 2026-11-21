package com.ouroboros.statistics.reporting.runtime.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefaultChartDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.service.ChartExecutionService;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionContextResolver;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionService;
import com.ouroboros.statistics.reporting.runtime.execution.TrustedDatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.exception.StatisticsReportingExceptionTranslator;
import com.ouroboros.statistics.reporting.runtime.result.ChartExecutionResponse;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.statistics.reporting.validation.ChartDefinitionValidator;
import com.ouroboros.statistics.reporting.validation.ChartParameterRules;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/runtime/statistics-reporting/charts")
public class ChartExecutionController {
  private final DefaultDatasetDefinitionCenter datasetDefinitionCenter;
  private final DefaultChartDefinitionCenter chartDefinitionCenter;
  private final DatasetExecutionService datasetExecutionService;
  private final ChartExecutionService chartExecutionService;
  private final ChartDefinitionValidator chartDefinitionValidator;
  private final BiPredicate<DatasetDefinition, DatasetExecutionRequest> exportPermissionChecker;
  private final DatasetExecutionContextResolver executionContextResolver;

  public ChartExecutionController(DefaultDatasetDefinitionCenter datasetDefinitionCenter,
                                  DefaultChartDefinitionCenter chartDefinitionCenter,
                                  DatasetExecutionService datasetExecutionService,
                                  ChartExecutionService chartExecutionService,
                                  ChartDefinitionValidator chartDefinitionValidator,
                                  BiPredicate<DatasetDefinition, DatasetExecutionRequest> exportPermissionChecker,
                                  DatasetExecutionContextResolver executionContextResolver) {
    this.datasetDefinitionCenter = Objects.requireNonNull(datasetDefinitionCenter, "datasetDefinitionCenter is required");
    this.chartDefinitionCenter = Objects.requireNonNull(chartDefinitionCenter, "chartDefinitionCenter is required");
    this.datasetExecutionService = Objects.requireNonNull(datasetExecutionService, "datasetExecutionService is required");
    this.chartExecutionService = Objects.requireNonNull(chartExecutionService, "chartExecutionService is required");
    this.chartDefinitionValidator = Objects.requireNonNull(chartDefinitionValidator, "chartDefinitionValidator is required");
    this.exportPermissionChecker = Objects.requireNonNull(exportPermissionChecker, "exportPermissionChecker is required");
    this.executionContextResolver = Objects.requireNonNull(executionContextResolver, "executionContextResolver is required");
  }

  @PostMapping("/{chartName}/execute")
  public WebApiResult<ChartExecutionResponse> execute(@PathVariable("chartName") String chartName,
                                                       @RequestBody(required = false) DatasetExecutionRequest request) {
    try {
      return WebApiResult.success(chartExecutionService.execute(chartName, resolveRequest(request)));
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics chart execution failed");
    }
  }

  public WebApiResult<Map<String, Object>> export(@PathVariable("chartName") String chartName,
                                                  @RequestBody(required = false) DatasetExecutionRequest request) {
    try {
      ChartDefinition chart = loadChart(chartName);
      DatasetDefinition dataset = loadDataset(chart.getDatasetName());
      chartDefinitionValidator.validate(dataset, chart);
      DatasetExecutionRequest resolvedRequest = resolveRequest(request);
      resolvedRequest.setDatasetName(dataset.getName());
      if (!exportPermissionChecker.test(dataset, resolvedRequest)) {
        throw StatisticsReportingExceptionTranslator.exportDenied();
      }
      resolvedRequest.setExport(true);
      DatasetResult result = datasetExecutionService.execute(resolvedRequest);
      return WebApiResult.success(buildExportPayload(dataset, chart, result));
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics chart export failed");
    }
  }

  @PostMapping("/{chartName}/export")
  public Object exportHttp(@PathVariable("chartName") String chartName,
                           @RequestBody(required = false) DatasetExecutionRequest request,
                           @org.springframework.web.bind.annotation.RequestParam(name = "format", defaultValue = "json") String format,
                           jakarta.servlet.http.HttpServletResponse response) {
    try {
      if (!"json".equals(format) && !"xlsx".equals(format)) {
        throw new com.ouroboros.exception.BusinessException("不支持的导出格式: " + format);
      }
      var result = export(chartName, request);
      if ("xlsx".equals(format)) {
        com.ouroboros.web.FileResponses.write(StatisticsExportFile.create(chartName, result.getData()), response);
        response.flushBuffer();
        return null;
      }
      return result;
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics export file failed");
    }
  }

  private DatasetExecutionRequest resolveRequest(DatasetExecutionRequest request) {
    DatasetExecutionRequest source = request == null ? new DatasetExecutionRequest() : request;
    return TrustedDatasetExecutionRequest.trust(source, executionContextResolver.resolve(source));
  }

  private ChartDefinition loadChart(String chartName) {
    return chartDefinitionCenter.getDefinition(chartName)
        .orElseThrow(() -> new IllegalArgumentException("chart definition not found: " + chartName));
  }

  private DatasetDefinition loadDataset(String datasetName) {
    if (datasetName == null || datasetName.isBlank()) {
      throw new IllegalArgumentException("datasetName is required");
    }
    return datasetDefinitionCenter.getDefinition(datasetName)
        .orElseThrow(() -> new IllegalArgumentException("dataset definition not found: " + datasetName));
  }

  private Map<String, Object> buildExportPayload(DatasetDefinition dataset,
                                                 ChartDefinition chart,
                                                 DatasetResult result) {
    List<DatasetFieldDefinition> exportFields = resolveExportFields(dataset, chart, result);
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("columns", toFieldPayloads(exportFields));
    payload.put("rows", filterRows(result, exportFields));
    payload.put("diagnostics", result == null ? null : result.getDiagnostics());
    payload.put("appliedParameters", result == null ? null : result.getAppliedParameters());
    return payload;
  }

  private List<DatasetFieldDefinition> resolveExportFields(DatasetDefinition dataset,
                                                           ChartDefinition chart,
                                                           DatasetResult result) {
    DatasetSchemaDefinition schema = result != null && result.getResolvedSchema() != null
        ? result.getResolvedSchema()
        : dataset.getSchema();
    List<DatasetFieldDefinition> exportFields = new ArrayList<DatasetFieldDefinition>();
    if (schema == null || schema.getFields() == null) {
      return exportFields;
    }
    List<String> candidateNames = ChartParameterRules.referencedFields(chart);
    for (DatasetFieldDefinition field : schema.getFields()) {
      if (field != null && field.isExportable()
          && (candidateNames.isEmpty() || candidateNames.contains(field.getName()))) {
        exportFields.add(field);
      }
    }
    return exportFields;
  }

  private List<Map<String, Object>> filterRows(DatasetResult result, List<DatasetFieldDefinition> exportFields) {
    List<Map<String, Object>> filteredRows = new ArrayList<Map<String, Object>>();
    if (result == null || result.getRows() == null) {
      return filteredRows;
    }
    for (Map<String, Object> row : result.getRows()) {
      Map<String, Object> filteredRow = new LinkedHashMap<String, Object>();
      for (DatasetFieldDefinition exportField : exportFields) {
        filteredRow.put(exportField.getName(), row == null ? null : row.get(exportField.getName()));
      }
      filteredRows.add(filteredRow);
    }
    return filteredRows;
  }

  private List<Map<String, Object>> toFieldPayloads(List<DatasetFieldDefinition> fields) {
    List<Map<String, Object>> payloads = new ArrayList<Map<String, Object>>();
    for (DatasetFieldDefinition field : fields) {
      Map<String, Object> payload = new LinkedHashMap<String, Object>();
      payload.put("name", field.getName());
      payload.put("title", field.getTitle());
      payload.put("dataType", field.getDataType());
      payload.put("semanticRole", field.getSemanticRole());
      payload.put("exportable", field.getExportable());
      payloads.add(payload);
    }
    return payloads;
  }
}
