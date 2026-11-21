package com.ouroboros.statistics.reporting.runtime.execution;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;

@FunctionalInterface
public interface DatasetPermissionInjector {

  DatasetExecutionRequest inject(DatasetDefinition dataset, DatasetExecutionRequest request);

  default boolean canExport(DatasetDefinition dataset, DatasetExecutionRequest request) {
    return true;
  }
}
