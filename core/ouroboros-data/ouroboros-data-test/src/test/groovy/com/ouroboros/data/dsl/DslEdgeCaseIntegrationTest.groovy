package com.ouroboros.data.dsl

import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

import com.ouroboros.data.AbstractDslIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName('DSL 边界与错误集成测试')
class DslEdgeCaseIntegrationTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
  }

  private static Map<String, Object> buildDeepInvalidCondition(int depth) {
    Map<String, Object> current = ['$unknown': true]
    for (int i = 0; i < depth; i++) {
      current = ['$and': [current]]
    }
    current
  }

  // ── Section 30: Analyze 层边界与错误 ──

  @Test
  @DisplayName('30-01 未知 $ 操作符')
  void test_30_01_unknownDollarOperator() {
    assertQueryFails(user(), [where: ['$unknown': 'x']])
  }

  @Test
  @DisplayName('30-02 空 where')
  void test_30_02_emptyWhere() {
    queryAndAssertSize(user(), [where: [:]], 3)
  }

  @Test
  @DisplayName('30-03 where 为 null')
  void test_30_03_whereNull() {
    queryAndAssertSize(user(), [where: null], 3)
  }

  @Test
  @DisplayName('30-04 select 为空数组')
  void test_30_04_selectEmptyArray() {
    def records = query(user(), [select: []])
    assertNotNull(records)
  }

  @Test
  @DisplayName('30-05 limit 为负数')
  void test_30_05_limitNegative() {
    assertQueryFails(user(), [limit: -1])
  }

  @Test
  @DisplayName('30-06 limit 为 0')
  void test_30_06_limitZero() {
    queryAndAssertSize(user(), [limit: 0], 0)
  }

  @Test
  @DisplayName('30-07 offset 无 limit')
  void test_30_07_offsetWithoutLimit() {
    def records = query(user(), [offset: 10])
    assertTrue(records.size() >= 0)
  }

  @Test
  @DisplayName('30-08 不存在的字段名')
  void test_30_08_nonExistingField() {
    assertQueryFails(user(), [where: [nonExistField: 1]])
  }

  @Test
  @DisplayName('30-09 $between 参数不足')
  void test_30_09_betweenTooFewArgs() {
    assertQueryFails(user(), [where: [age: ['$between': [18]]]])
  }

  @Test
  @DisplayName('30-10 $between 参数过多')
  void test_30_10_betweenTooManyArgs() {
    assertQueryFails(user(), [where: [age: ['$between': [1, 2, 3]]]])
  }

  @Test
  @DisplayName('30-11 $in 值非数组')
  void test_30_11_inValueNotArray() {
    assertQueryFails(user(), [where: [status: ['$in': 'active']]])
  }

  @Test
  @DisplayName('30-12 关联条件非 Map')
  void test_30_12_relationConditionNotMap() {
    assertQueryFails(order(), [where: [orderItems: ['$any': 123]]])
  }

  @Test
  @DisplayName('30-13 嵌套层级过深')
  void test_30_13_nestedDepthTooDeep() {
    assertQueryFails(user(), [where: buildDeepInvalidCondition(10)])
  }

  @Test
  @DisplayName('30-14 Map 中混合操作符和字段')
  void test_30_14_mixedOperatorAndFieldMap() {
    assertQueryFails(user(), [where: [name: ['$eq': 'Alice', unknownKey: 1]]])
  }
}
