package com.ouroboros.data.dsl

import static org.junit.jupiter.api.Assertions.assertTrue

import com.ouroboros.data.AbstractDslIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName('DSL Golden Case 集成测试')
class DslGoldenCaseIntegrationTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
  }

  // ── Section 31: 端到端 Golden Case ──

  @Test
  @DisplayName('G-01 复杂订单查询')
  void test_31_G01_complexOrderQuery() {
    def dsl = [
      select  : ['id', 'amount', 'createdAt', [orderStatus: 'status']],
      where   : [
        '$and': [
          [status: 'PENDING'],
          [amount: ['$gte': 100, '$lte': 10000]],
          [createdAt: ['$gte': '2025-01-01']],
          [user: [name: ['$contains': 'A']]],
          [user: [department: [name: '技术部']]],
          ['$or': [
            [orderItems: ['$any': [price: ['$gt': 500]]]],
            [reviews: ['$all': [rating: ['$gte': 4]]]]
          ]]
        ]
      ],
      order   : [[createdAt: 'desc'], [amount: 'asc']],
      limit   : 20,
      offset  : 0,
      populate: ['user', 'orderItems']
    ]

    def records = query(order(), dsl)
    assertTrue(records.size() >= 1)
    assertTrue(records.every { it.get('user') instanceof Map })
    assertTrue(records.every { it.get('orderItems') instanceof List })
  }

  @Test
  @DisplayName('G-02 分组统计 + HAVING')
  void test_31_G02_groupByHaving() {
    def dsl = [
      select: ['status', [cnt: ['count', ['*']]], [totalAmount: ['sum', ['amount']]]],
      where : [createdAt: ['$gte': '2025-01-01']],
      group : ['status'],
      having: ['and', ['>', ['count', ['*']], 0], ['>', ['sum', ['amount']], 100]],
      order : [[cnt: 'desc']]
    ]

    def records = query(order(), dsl)
    assertTrue(records.size() >= 1)
  }

  @Test
  @DisplayName('G-03 多对一 + 一对多 + 逻辑嵌套')
  void test_31_G03_m2oO2mNestedLogic() {
    def dsl = [
      where   : [
        '$or': [
          ['$and': [
            [user: [department: [name: '技术部']]],
            [amount: ['$gte': 150]]
          ]],
          [orderItems: ['$any': [productName: ['$contains': 'iPhone']]]]
        ]
      ],
      order   : [[createdAt: 'desc']],
      limit   : 50,
      populate: ['user', 'orderItems']
    ]

    def records = query(order(), dsl)
    assertTrue(records.size() >= 1)
  }

  @Disabled('design-only: 包含符号关联速记与关联聚合扩展语法，待后续版本实现')
  @Test
  @DisplayName('G-04 Design-only Golden Case')
  void test_31_G04_designOnlyGoldenCase() {
    def dsl = [
      where: ['@user#department.name': '技术部']
    ]

    query(order(), dsl)
  }
}
