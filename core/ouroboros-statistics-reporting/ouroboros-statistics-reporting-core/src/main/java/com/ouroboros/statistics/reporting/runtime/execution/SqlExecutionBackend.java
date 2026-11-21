package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface SqlExecutionBackend {

  SqlQueryResult execute(SqlDataSourceContext context, String sql, Map<String, Object> parameters);

  default SqlQueryResult execute(SqlDataSourceContext context, String sql, Map<String, Object> parameters,
                                 DatasetExecutionBudget budget, int maxRows) {
    if (budget != null) budget.remainingSeconds();
    SqlQueryResult result = execute(context, sql, parameters);
    if (budget != null) budget.remainingSeconds();
    return result;
  }

  class SqlQueryResult {
    private List<Map<String, Object>> rows = new ArrayList<>();
    private List<String> columnNames = new ArrayList<>();

    public List<Map<String, Object>> getRows() {
      return rows;
    }

    public SqlQueryResult setRows(List<Map<String, Object>> rows) {
      this.rows = rows;
      return this;
    }

    public List<String> getColumnNames() {
      return columnNames;
    }

    public SqlQueryResult setColumnNames(List<String> columnNames) {
      this.columnNames = columnNames;
      return this;
    }
  }
}
