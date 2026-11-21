package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.Map;

public interface DatasetExecutionContextResolver {

  Map<String, Object> resolve(DatasetExecutionRequest request);
}
