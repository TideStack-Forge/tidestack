package com.ouroboros.statistics.reporting.runtime.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.businessmodel.BusinessModelCenter;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionContextResolver;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionService;
import com.ouroboros.statistics.reporting.runtime.execution.TrustedDatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.exception.StatisticsReportingExceptionTranslator;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/runtime/composite-view")
public class CompositeViewExecutionController {
  private final DatasetExecutionService datasetExecutionService;
  private final DatasetExecutionContextResolver executionContextResolver;

  public CompositeViewExecutionController(DatasetExecutionService datasetExecutionService,
                                          DatasetExecutionContextResolver executionContextResolver) {
    this.datasetExecutionService = Objects.requireNonNull(datasetExecutionService, "datasetExecutionService is required");
    this.executionContextResolver = Objects.requireNonNull(executionContextResolver, "executionContextResolver is required");
  }

  @PostMapping("/{compositeViewName}/execute")
  public WebApiResult<DatasetResult> execute(@PathVariable("compositeViewName") String compositeViewName,
                                              @RequestBody(required = false) DatasetExecutionRequest request) {
    try {
      CompositeViewBusinessModelMeta compositeView = loadCompositeView(compositeViewName);
      return WebApiResult.success(new com.ouroboros.statistics.reporting.runtime.service.CompositeViewQueryService(
          datasetExecutionService, executionContextResolver).execute(compositeView, request));
    } catch (Exception error) {
      throw StatisticsReportingExceptionTranslator.translate(error, "statistics composite-view execution failed");
    }
  }

  private CompositeViewBusinessModelMeta loadCompositeView(String compositeViewName) {
    return BusinessModelCenter.getBusinessModel(compositeViewName)
        .filter(com.ouroboros.businessmodel.BusinessModel::isEnabled)
        .flatMap(com.ouroboros.businessmodel.BusinessModel::compositeView)
        .orElseThrow(() -> new IllegalArgumentException("composite-view.not-found: " + compositeViewName));
  }


}
