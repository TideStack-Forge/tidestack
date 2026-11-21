package com.ouroboros.data.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.data.AbstractDataIntegrationTest;
import com.ouroboros.data.fixture.TestDataFixture;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;

/**
 * 聚合操作集成测试
 * <p>
 * 测试 COUNT/SUM/AVG/MIN/MAX/GROUP BY/HAVING 等聚合功能。
 * <p>
 * 聚合函数使用 S 表达式语法：
 * <pre>
 * // SUM(amount) as total
 * {total: ['sum', ['field', 'amount']]}
 *
 * // AVG(amount) as average
 * {average: ['avg', ['field', 'amount']]}
 *
 * // MIN(amount), MAX(amount)
 * {min_amount: ['min', ['field', 'amount']], max_amount: ['max', ['field', 'amount']]}
 * </pre>
 *
 * @since 1.0.0-beta.2
 */
class AggregationIntegrationTest extends AbstractDataIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData();
  }

  @Test
  void count_withWhere_shouldReturnCount() {
    Map<String, Object> where = Collections.singletonMap("status", "COMPLETED");

    Try<Long> result = order().count(where);

    assertTrue(result.isSuccess(), "Count should succeed");
    assertEquals(2L, result.get(), "Should have 2 completed orders");
  }

  @Test
  void sum_orderAmount_shouldReturnTotal() {
    Map<String, Object> query = new HashMap<>();
    // S表达式：{total: ['sum', ['field', 'amount']]}
    query.put("select", Arrays.asList(
        Collections.singletonMap("total", Arrays.asList("sum", Arrays.asList("field", "amount")))
    ));

    Try<RecordList> result = order().query(query);

    assertTrue(result.isSuccess(), "SUM query should succeed: " + (result.isFailure() ? result.getCause().getMessage() : ""));
    RecordList records = result.get();
    assertEquals(1, records.size(), "Should return 1 aggregated row");

    Record record = records.get(0);
    // NOTE: 别名会被转换为大写
    Object totalObj = record.get("TOTAL");
    assertNotNull(totalObj, "Total should not be null");

    // 预期总和: 100 + 200 + 150 + 300 + 250 = 1000
    double total = ((Number) totalObj).doubleValue();
    assertEquals(1000.0, total, 0.01, "Total amount should be 1000");
  }

  @Test
  void avg_orderAmount_shouldReturnAverage() {
    Map<String, Object> query = new HashMap<>();
    // S表达式：{average: ['avg', ['field', 'amount']]}
    query.put("select", Arrays.asList(
        Collections.singletonMap("average", Arrays.asList("avg", Arrays.asList("field", "amount")))
    ));

    Try<RecordList> result = order().query(query);

    assertTrue(result.isSuccess(), "AVG query should succeed: " + (result.isFailure() ? result.getCause().getMessage() : ""));
    RecordList records = result.get();
    assertEquals(1, records.size(), "Should return 1 aggregated row");

    Record record = records.get(0);
    // NOTE: 别名会被转换为大写
    Object avgObj = record.get("AVERAGE");
    assertNotNull(avgObj, "Average should not be null");

    // 预期平均值: 1000 / 5 = 200
    double average = ((Number) avgObj).doubleValue();
    assertEquals(200.0, average, 0.01, "Average amount should be 200");
  }

  @Test
  void minMax_orderAmount_shouldReturnBounds() {
    Map<String, Object> query = new HashMap<>();
    // S表达式：[{min_amount: ['min', ['field', 'amount']]}, {max_amount: ['max', ['field', 'amount']]}]
    query.put("select", Arrays.asList(
        Collections.singletonMap("min_amount", Arrays.asList("min", Arrays.asList("field", "amount"))),
        Collections.singletonMap("max_amount", Arrays.asList("max", Arrays.asList("field", "amount")))
    ));

    Try<RecordList> result = order().query(query);

    assertTrue(result.isSuccess(), "MIN/MAX query should succeed: " + (result.isFailure() ? result.getCause().getMessage() : ""));
    RecordList records = result.get();
    assertEquals(1, records.size(), "Should return 1 aggregated row");

    Record record = records.get(0);
    // NOTE: 别名会被转换为大写
    double minAmount = ((Number) record.get("MIN_AMOUNT")).doubleValue();
    double maxAmount = ((Number) record.get("MAX_AMOUNT")).doubleValue();

    assertEquals(100.0, minAmount, 0.01, "Min amount should be 100");
    assertEquals(300.0, maxAmount, 0.01, "Max amount should be 300");
  }

  @Test
  void groupBy_department_shouldGroupResults() {
    Map<String, Object> query = new HashMap<>();
    // S表达式：['departmentId', {user_count: ['count', ['field', 'id']]}]
    query.put("select", Arrays.asList(
        "departmentId",
        Collections.singletonMap("user_count", Arrays.asList("count", Arrays.asList("field", "id")))
    ));
    query.put("groupBy", Arrays.asList("departmentId"));

    Try<RecordList> result = user().query(query);

    assertTrue(result.isSuccess(), "GROUP BY query should succeed: " + (result.isFailure() ? result.getCause().getMessage() : ""));
    RecordList records = result.get();
    assertEquals(2, records.size(), "Should return 2 groups (TECH, HR)");

    // 查找 TECH 部门的统计
    Record techGroup = records.stream()
        .filter(r -> TestDataFixture.Ids.DEPT_TECH.equals(r.get("DEPARTMENTID")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("TECH group not found"));

    long techCount = ((Number) techGroup.get("USER_COUNT")).longValue();
    assertEquals(2L, techCount, "TECH department should have 2 users");
  }

  @Test
  void having_filterGroups_shouldFilterResults() {
    Map<String, Object> query = new HashMap<>();
    // S表达式：['userId', {order_count: ['count', ['field', 'id']]}, {total_amount: ['sum', ['field', 'amount']]}]
    query.put("select", Arrays.asList(
        "userId",
        Collections.singletonMap("order_count", Arrays.asList("count", Arrays.asList("field", "id"))),
        Collections.singletonMap("total_amount", Arrays.asList("sum", Arrays.asList("field", "amount")))
    ));
    query.put("groupBy", Arrays.asList("userId"));
    // S-expression: (>= (count (field id)) 2)
    query.put("having", Arrays.asList(">=",
        Arrays.asList("count", Arrays.asList("field", "id")),
        2
    ));

    Try<RecordList> result = order().query(query);

    assertTrue(result.isSuccess(), "HAVING query should succeed: " + (result.isFailure() ? result.getCause().getMessage() : ""));
    RecordList records = result.get();

    // 只有 Alice (2个订单) 和 Charlie (2个订单) 满足条件
    assertEquals(2, records.size(), "Should return 2 users with >= 2 orders");

    // 验证所有结果的订单数都 >= 2
    for (Record record : records) {
      long orderCount = ((Number) record.get("ORDER_COUNT")).longValue();
      assertTrue(orderCount >= 2, "All groups should have >= 2 orders");
    }
  }
}
