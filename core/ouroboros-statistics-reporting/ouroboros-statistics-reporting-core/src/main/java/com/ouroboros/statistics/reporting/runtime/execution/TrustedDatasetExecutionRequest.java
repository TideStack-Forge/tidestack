package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TrustedDatasetExecutionRequest extends DatasetExecutionRequest {

  private TrustedDatasetExecutionRequest() {
  }

  public static DatasetExecutionRequest trust(DatasetExecutionRequest request,
                                              Map<String, Object> executionContext) {
    DatasetExecutionRequest source = request == null ? new DatasetExecutionRequest() : request.copy();
    TrustedDatasetExecutionRequest trusted = new TrustedDatasetExecutionRequest();
    trusted.setDatasetName(source.getDatasetName());
    trusted.setExport(source.isExport());
    trusted.setExecutionBudget(source.getExecutionBudget());
    trusted.setCompositeViewName(source.getCompositeViewName());
    trusted.setRuntimeParameters(source.getRuntimeParameters());
    trusted.setAppliedParameters(source.getAppliedParameters());
    trusted.setExecutionContext(copyMap(executionContext));
    trusted.setDynamicFilter(source.getDynamicFilter());
    trusted.setPage(source.getPage());
    return trusted;
  }

  private static Map<String, Object> copyMap(Map<String, Object> source) {
    Map<String, Object> copied = new LinkedHashMap<String, Object>();
    if (source != null) {
      copied.putAll(source);
    }
    return copied;
  }
}
