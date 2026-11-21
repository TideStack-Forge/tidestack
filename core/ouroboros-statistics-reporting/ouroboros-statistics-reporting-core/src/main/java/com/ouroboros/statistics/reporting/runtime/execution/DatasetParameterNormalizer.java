package com.ouroboros.statistics.reporting.runtime.execution;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;

public interface DatasetParameterNormalizer {
  DatasetExecutionRequest normalize(DatasetDefinition dataset, DatasetExecutionRequest request);
}
