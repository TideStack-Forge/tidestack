package com.ouroboros.statistics.reporting.runtime.execution;

import org.junit.jupiter.api.Test;
import com.ouroboros.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;

class DatasetExecutionBudgetTest {
  @Test
  void rejectsTheFirstExcessRowUsingTheOperationSpecificLimit() {
    var policy = new DatasetExecutionPolicy(30, 2, 3, "test");
    var query = new DatasetExecutionBudget(policy, false);
    query.checkRows(2);
    assertTrue(assertThrows(BusinessException.class, () -> query.checkRows(3)).getMessage()
        .contains("dataset.page-size-exceeded"));
    var export = new DatasetExecutionBudget(policy, true);
    export.checkRows(3);
    assertTrue(assertThrows(BusinessException.class, () -> export.checkRows(4)).getMessage()
        .contains("dataset.export-row-limit-exceeded"));
  }

  @Test
  void keepsOneDeadlineAndPropagatesTheOriginalDriverFailure() throws InterruptedException {
    var budget = new DatasetExecutionBudget(new DatasetExecutionPolicy(1, 2, 3, "test"), false);
    assertEquals(1, budget.remainingSeconds());
    Thread.sleep(1050);
    assertTrue(assertThrows(BusinessException.class, budget::remainingSeconds).getMessage().contains("查询超过 1 秒"));
    var driver = new IllegalStateException("driver cancelled");
    assertSame(driver, budget.timeout(driver).getCause());
  }
}
