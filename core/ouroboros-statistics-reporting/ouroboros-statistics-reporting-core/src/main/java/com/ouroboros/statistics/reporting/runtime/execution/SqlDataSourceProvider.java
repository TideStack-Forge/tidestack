package com.ouroboros.statistics.reporting.runtime.execution;

import com.ouroboros.statistics.reporting.definition.query.SqlQueryDefinition;

@FunctionalInterface
public interface SqlDataSourceProvider {

  SqlDataSourceContext resolve(SqlQueryDefinition queryDefinition, DatasetExecutionRequest request);
}
