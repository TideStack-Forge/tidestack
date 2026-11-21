package com.ouroboros.statistics.reporting.runtime.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.PathVariable;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.definition.schema.DatasetSchemaDefinition;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionContextResolver;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionService;
import com.ouroboros.statistics.reporting.runtime.execution.TrustedDatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.exception.StatisticsReportingExceptionTranslator;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/statistics-reporting/datasets")
public class DatasetExecutionController {
  private final DefaultDatasetDefinitionCenter datasetDefinitionCenter;
  private final DatasetExecutionService datasetExecutionService;
  private final BiPredicate<DatasetDefinition, DatasetExecutionRequest> exportPermissionChecker;
  private final DatasetExecutionContextResolver executionContextResolver;

  public DatasetExecutionController(DefaultDatasetDefinitionCenter datasetDefinitionCenter,
                                    DatasetExecutionService datasetExecutionService,
                                    BiPredicate<DatasetDefinition, DatasetExecutionRequest> exportPermissionChecker) {
    this(datasetDefinitionCenter, datasetExecutionService, exportPermissionChecker,
        request -> request == null ? new LinkedHashMap<String, Object>() : request.getExecutionContext());
  }

  public DatasetExecutionController(DefaultDatasetDefinitionCenter datasetDefinitionCenter,
                                    DatasetExecutionService datasetExecutionService,
                                    BiPredicate<DatasetDefinition, DatasetExecutionRequest> exportPermissionChecker,
                                    DatasetExecutionContextResolver executionContextResolver) {
    this.datasetDefinitionCenter = Objects.requireNonNull(datasetDefinitionCenter, "datasetDefinitionCenter is required");
    this.datasetExecutionService = Objects.requireNonNull(datasetExecutionService, "datasetExecutionService is required");
    this.exportPermissionChecker = Objects.requireNonNull(exportPermissionChecker, "exportPermissionChecker is required");
    this.executionContextResolver = Objects.requireNonNull(executionContextResolver, "executionContextResolver is required");
  }

  @PostMapping("/{datasetName}/execute")
  public WebApiResult<DatasetResult> execute(@PathVariable("datasetName") String datasetName, @RequestBody DatasetExecutionRequest request) {
    try {
      return WebApiResult.success(datasetExecutionService.execute(resolveRequest(datasetName, request)));
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics dataset execution failed");
    }
  }

  public WebApiResult<Map<String, Object>> export(@PathVariable("datasetName") String datasetName, @RequestBody DatasetExecutionRequest request) {
    try {
      DatasetExecutionRequest trustedRequest = resolveRequest(datasetName, request);
      DatasetDefinition dataset = loadDataset(trustedRequest);
      if (!exportPermissionChecker.test(dataset, trustedRequest)) {
        throw StatisticsReportingExceptionTranslator.exportDenied();
      }
      trustedRequest.setExport(true);
      DatasetResult result = datasetExecutionService.execute(trustedRequest);
      return WebApiResult.success(buildExportPayload(dataset, result));
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics dataset export failed");
    }
  }

  @PostMapping("/{datasetName}/export")
  public Object exportHttp(@PathVariable("datasetName") String datasetName,
                           @RequestBody DatasetExecutionRequest request,
                           @org.springframework.web.bind.annotation.RequestParam(name = "format", defaultValue = "json") String format,
                           jakarta.servlet.http.HttpServletResponse response) {
    try {
      if (!"json".equals(format) && !"xlsx".equals(format)) {
        throw new com.ouroboros.exception.BusinessException("不支持的导出格式: " + format);
      }
      var result = export(datasetName, request);
      if ("xlsx".equals(format)) {
        com.ouroboros.web.FileResponses.write(StatisticsExportFile.create(datasetName, result.getData()), response);
        response.flushBuffer();
        return null;
      }
      return result;
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics export file failed");
    }
  }

  private DatasetExecutionRequest resolveRequest(String datasetName, DatasetExecutionRequest request) {
    request = request == null ? new DatasetExecutionRequest() : request.copy();
    request.setDatasetName(datasetName);
    Map<String, Object> resolvedContext = executionContextResolver.resolve(request);
    return TrustedDatasetExecutionRequest.trust(request, resolvedContext);
  }

  private Map<String, Object> buildExportPayload(DatasetDefinition dataset, DatasetResult result) {
    List<DatasetFieldDefinition> exportFields = resolveExportFields(dataset, result);
    Map<String, Object> payload = new LinkedHashMap<String, Object>();
    payload.put("columns", toFieldPayloads(exportFields));
    payload.put("rows", filterRows(result, exportFields));
    payload.put("diagnostics", result == null ? null : result.getDiagnostics());
    payload.put("appliedParameters", result == null ? null : result.getAppliedParameters());
    return payload;
  }

  private List<DatasetFieldDefinition> resolveExportFields(DatasetDefinition dataset, DatasetResult result) {
    DatasetSchemaDefinition schema = result != null && result.getResolvedSchema() != null
        ? result.getResolvedSchema()
        : dataset.getSchema();
    List<DatasetFieldDefinition> exportFields = new ArrayList<DatasetFieldDefinition>();
    if (schema == null || schema.getFields() == null) {
      return exportFields;
    }
    for (DatasetFieldDefinition field : schema.getFields()) {
      if (field != null && field.isExportable()) {
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

  private DatasetDefinition loadDataset(DatasetExecutionRequest request) {
    String datasetName = request == null ? null : request.getDatasetName();
    if (datasetName == null || datasetName.isBlank()) {
      throw new IllegalArgumentException("datasetName is required");
    }
    return datasetDefinitionCenter.getDefinition(datasetName)
        .orElseThrow(() -> new IllegalArgumentException("dataset definition not found: " + datasetName));
  }
}
