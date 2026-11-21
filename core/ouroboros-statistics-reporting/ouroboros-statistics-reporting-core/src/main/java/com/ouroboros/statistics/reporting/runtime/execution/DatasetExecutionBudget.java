package com.ouroboros.statistics.reporting.runtime.execution;

import com.ouroboros.exception.BusinessException;

/** Shared monotonic deadline for COUNT, SQL execution and result reading. */
public final class DatasetExecutionBudget {
  private final DatasetExecutionPolicy policy;
  private final long started = System.nanoTime();
  private final boolean export;

  public DatasetExecutionBudget(DatasetExecutionPolicy policy, boolean export) {
    this.policy = policy;
    this.export = export;
  }

  public DatasetExecutionPolicy policy() { return policy; }
  public boolean exporting() { return export; }
  public int rowLimit() { return export ? policy.maxExportRows() : policy.maxPageSize(); }

  public int remainingSeconds() {
    long remaining = policy.queryTimeoutSeconds() * 1000000000L - (System.nanoTime() - started);
    if (remaining <= 0) throw timeout(null);
    return (int) Math.max(1, (remaining + 999999999L) / 1000000000L);
  }

  public void checkRows(long rows) {
    if (rows > rowLimit()) {
      throw new BusinessException((export ? "dataset.export-row-limit-exceeded: 导出" : "dataset.page-size-exceeded: 查询")
          + "最多允许 " + rowLimit() + " 行，请收紧查询条件");
    }
  }

  public BusinessException timeout(Throwable cause) {
    return new BusinessException("dataset.query-timeout: 查询超过 " + policy.queryTimeoutSeconds() + " 秒", cause);
  }
}
