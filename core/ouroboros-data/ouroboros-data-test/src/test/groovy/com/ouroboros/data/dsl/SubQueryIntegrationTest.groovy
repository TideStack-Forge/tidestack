package com.ouroboros.data.dsl

import static org.junit.jupiter.api.Assertions.assertTrue

import com.ouroboros.data.AbstractDslIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@DisplayName('子查询与 EXISTS 集成测试')
class SubQueryIntegrationTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
  }

  // ── Section 23: 子查询（design-only） ──

  @Disabled('design-only: $in + 子查询 Map 右值到 SUB_QUERY 的自动转换链路未冻结')
  @Test
  @DisplayName('23-01 WHERE IN 子查询')
  void test_23_01_whereInSubQuery() {
    def dsl = [
      where: [
        userId: [
          '$in': [
            select: ['id'],
            from  : 'User',
            where : [status: 'active']
          ]
        ]
      ]
    ]

    query(order(), dsl)
  }

  @Disabled('design-only: exists 参数 Map -> SUB_QUERY 入口未在集成层固化')
  @Test
  @DisplayName('23-02 EXISTS 子查询')
  void test_23_02_existsSubQuery() {
    def dsl = [
      where: ['exists', [select: ['1'], from: 'Order', where: [status: 'COMPLETED']]]
    ]

    query(user(), dsl)
  }

  // ── Section 24: EXISTS 独立操作符（undocumented） ──

  @Tag('undocumented')
  @Test
  @DisplayName('24-01 S 表达式中 EXISTS')
  void test_24_01_existsStandaloneOperator() {
    def dsl = [
      where: ['exists', [select: ['1'], from: 'Order', where: [status: 'COMPLETED']]]
    ]

    def records = query(user(), dsl)
    assertTrue(records.size() >= 1)
  }
}
