package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@com.ouroboros.model.annotation.AllowDirectJDBC("governed statistics SQL execution with bound parameters, deadlines and row limits")
public class JdbcSqlExecutionBackend implements SqlExecutionBackend {

  @Override
  public SqlQueryResult execute(SqlDataSourceContext context, String sql, Map<String, Object> parameters) {
    return execute(context, sql, parameters, null, 0);
  }

  @Override
  public SqlQueryResult execute(SqlDataSourceContext context, String sql, Map<String, Object> parameters,
                                DatasetExecutionBudget budget, int maxRows) {
    if (context == null || context.getDataSource() == null) {
      throw new IllegalArgumentException("sql data source is required");
    }
    NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(context.getDataSource());
    if (budget != null) template.getJdbcTemplate().setQueryTimeout(budget.remainingSeconds());
    template.getJdbcTemplate().setMaxRows(maxRows);
    try {
      return template.query(toNamedParameterSql(sql), parameters == null ? Map.of() : parameters, resultSet -> {
        List<String> columns = new ArrayList<>();
        var metadata = resultSet.getMetaData();
        for (int i = 1; i <= metadata.getColumnCount(); i++) columns.add(metadata.getColumnLabel(i));
        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
          if (budget != null) {
            try { budget.remainingSeconds(); }
            catch (com.ouroboros.exception.BusinessException timeout) {
              try { resultSet.getStatement().cancel(); }
              catch (java.sql.SQLException cancellation) { timeout.addSuppressed(cancellation); }
              throw timeout;
            }
          }
          Map<String, Object> row = new LinkedHashMap<>();
          for (int i = 0; i < columns.size(); i++) row.put(columns.get(i), resultSet.getObject(i + 1));
          rows.add(row);
        }
        if (budget != null) budget.remainingSeconds();
        return new SqlQueryResult().setRows(rows).setColumnNames(columns);
      });
    } catch (org.springframework.dao.QueryTimeoutException timeout) {
      if (budget != null) throw budget.timeout(timeout);
      throw timeout;
    }
  }

  private String toNamedParameterSql(String sql) {
    if (sql == null) {
      return null;
    }
    return sql.replaceAll("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}", ":$1");
  }
}
