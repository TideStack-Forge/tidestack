package com.ouroboros.statistics.reporting.runtime.executor;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.result.DatasetResult;

public interface DatasetExecutor {
  default boolean requiresDataSource() {
    return false;
  }


  boolean supports(DatasetDefinition dataset);

  DatasetResult execute(DatasetDefinition dataset, DatasetExecutionRequest request);
}
