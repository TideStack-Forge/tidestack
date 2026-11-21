package com.ouroboros.data.dsl

import static org.junit.jupiter.api.Assertions.assertTrue

import com.ouroboros.data.AbstractDslIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName('JOIN 子句集成测试')
class JoinClauseIntegrationTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
  }

  // ── Section 15: JOIN 子句 ──

  @Test
  @DisplayName('15-01 LEFT JOIN')
  void test_15_01_leftJoin() {
    def dsl = [
      select  : ['id', 'name'],
      leftJoin: ['d': 'Department', 'on': ['departmentId': 'id']]
    ]

    queryAndAssertSize(user(), dsl, 3)
  }

  @Test
  @DisplayName('15-02 INNER JOIN')
  void test_15_02_innerJoin() {
    def dsl = [
      select   : ['id', 'name'],
      innerJoin: ['d': 'Department', 'on': ['departmentId': 'id']]
    ]

    queryAndAssertSize(user(), dsl, 3)
  }

  @Test
  @DisplayName('15-03 RIGHT JOIN')
  void test_15_03_rightJoin() {
    def dsl = [
      select   : ['id', 'name'],
      rightJoin: ['d': 'Department', 'on': ['departmentId': 'id']]
    ]

    def records = query(user(), dsl)
    assertTrue(records.size() >= 2)
  }

  @Test
  @DisplayName('15-04 FULL JOIN（MySQL 方言明确拒绝）')
  void test_15_04_fullJoin() {
    def dsl = [
      select  : ['id', 'name'],
      fullJoin: ['d': 'Department', 'on': ['departmentId': 'id']]
    ]

    def result = user().query(dsl)
    assertTrue(result.isFailure(), 'MySQL 方言下 FULL JOIN 应明确失败')

    def messages = []
    def cause = result.getCause()
    while (cause != null) {
      messages << String.valueOf(cause.getMessage())
      cause = cause.getCause()
    }

    assertTrue(
      messages.any { it != null && it.contains('不支持 FULL JOIN') },
      "错误链应明确说明 FULL JOIN 不受支持，实际: ${messages}"
    )
  }

  @Test
  @DisplayName('15-05 CROSS JOIN')
  void test_15_05_crossJoin() {
    def dsl = [
      select   : ['id', 'name'],
      crossJoin: ['d': 'Department']
    ]

    queryAndAssertSize(user(), dsl, 6)
  }

  @Test
  @DisplayName('15-06 多表 JOIN')
  void test_15_06_multiJoin() {
    def dsl = [
      select  : ['id', 'name'],
      leftJoin: [
        ['o': 'Order', 'on': ['id': 'userId']],
        ['d': 'Department', 'on': ['departmentId': 'id']]
      ]
    ]

    def records = query(user(), dsl)
    assertTrue(records.size() >= 3)
  }

  @Test
  @DisplayName('15-07 JOIN + WHERE')
  void test_15_07_joinWithWhere() {
    def dsl = [
      leftJoin: ['d': 'Department', 'on': ['departmentId': 'id']],
      where   : [department: [code: 'TECH']]
    ]

    queryAndAssertSize(user(), dsl, 2)
  }

  @Test
  @DisplayName('15-08 ON 条件 Map 形式')
  void test_15_08_onMapCondition() {
    def dsl = [
      join: ['d': 'Department', 'on': ['departmentId': 'id']]
    ]

    queryAndAssertSize(user(), dsl, 3)
  }

  @Test
  @DisplayName('15-09 ON 条件 List 形式')
  void test_15_09_onListCondition() {
    def dsl = [
      join: ['d': 'Department', 'on': [['departmentId': 'id'], ['departmentId': 'id']]]
    ]

    queryAndAssertSize(user(), dsl, 3)
  }
}
