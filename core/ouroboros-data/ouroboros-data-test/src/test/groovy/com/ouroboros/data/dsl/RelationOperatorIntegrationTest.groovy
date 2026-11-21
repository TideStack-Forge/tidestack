package com.ouroboros.data.dsl

import com.ouroboros.data.AbstractDslIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName('一对多关联操作符与关联聚合集成测试')
class RelationOperatorIntegrationTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
  }

  // ── Section 21: 一对多关联操作符（$any/$all/$none） ──

  @Test
  @DisplayName('21-01 $any 基础')
  void test_21_01_anyBasic() {
    // ⚠️ 待确认：$any 别名解析链路（OperatorAlias / Normalize）是否稳定。
    def dsl = [where: [orderItems: ['$any': [productName: 'iPhone']]]]
    queryAndAssertSize(order(), dsl, 2)
  }

  @Test
  @DisplayName('21-02 $any 与比较操作符')
  void test_21_02_anyWithOperator() {
    def dsl = [where: [orderItems: ['$any': [price: ['$gt': 100]]]]]
    queryAndAssertSize(order(), dsl, 5)
  }

  @Test
  @DisplayName('21-03 $any 多条件')
  void test_21_03_anyWithMultiConditions() {
    def dsl = [where: [orderItems: ['$any': [productName: ['$contains': 'iPhone'], qty: ['$gte': 2]]]]]
    queryAndAssertSize(order(), dsl, 0)
  }

  @Test
  @DisplayName('21-04 $all')
  void test_21_04_allOperator() {
    // ⚠️ 空集合语义（vacuous truth）：ORDER_4 无 reviews，$all 应满足。
    def dsl = [where: [reviews: ['$all': [score: ['$gte': 4]]]]]
    queryAndAssertSize(order(), dsl, 3)
  }

  @Test
  @DisplayName('21-05 $none')
  void test_21_05_noneOperator() {
    // ⚠️ 空集合语义：ORDER_4 无 reviews，$none 应满足。
    def dsl = [where: [reviews: ['$none': [score: ['$lt': 3]]]]]
    queryAndAssertSize(order(), dsl, 4)
  }

  @Test
  @DisplayName('21-06 无前缀 any 兼容')
  void test_21_06_anyWithoutPrefix() {
    def dsl = [where: [orderItems: [any: [price: ['$gt': 100]]]]]
    queryAndAssertSize(order(), dsl, 5)
  }

  @Test
  @DisplayName('21-07 无前缀 all 兼容')
  void test_21_07_allWithoutPrefix() {
    def dsl = [where: [reviews: [all: [score: ['>=': 4]]]]]
    queryAndAssertSize(order(), dsl, 3)
  }

  @Test
  @DisplayName('21-08 无前缀 none 兼容')
  void test_21_08_noneWithoutPrefix() {
    def dsl = [where: [reviews: [none: [score: ['<': 3]]]]]
    queryAndAssertSize(order(), dsl, 4)
  }

  @Test
  @DisplayName('21-09 $any 条件非 Map')
  void test_21_09_anyConditionNotMap() {
    assertQueryFails(order(), [where: [orderItems: ['$any': 123]]])
  }

  @Test
  @DisplayName('21-10 $any 与 $none 同时出现')
  void test_21_10_anyAndNoneTogether() {
    def dsl = [
      where: [
        orderItems: ['$any' : [price: ['$gt': 500]]],
        reviews   : ['$none': [score: ['$lt': 3]]]
      ]
    ]

    queryAndAssertSize(order(), dsl, 3)
  }

  @Test
  @DisplayName('21-11 关联操作符与本表条件组合')
  void test_21_11_relationOperatorWithLocalField() {
    def dsl = [
      where: [
        status    : 'PENDING',
        orderItems: ['$any': [price: ['$gt': 1000]]]
      ]
    ]

    queryAndAssertSize(order(), dsl, 1)
  }

  // ── Section 22: 关联聚合 ──

  @Test
  @DisplayName('22-01 $count 关联聚合')
  void test_22_01_countRelationAggregation() {
    // ⚠️ 待确认：$count 在关联字段位置的解析入口是否与设计语义一致。
    def dsl = [where: [orderItems: ['$count': ['$gt': 2]]]]
    queryAndAssertSize(order(), dsl, 0)
  }

  @Disabled('design-only: $sum 关联聚合入口未实现（v1.3 planned）')
  @Test
  @DisplayName('22-02 $sum 关联聚合')
  void test_22_02_sumRelationAggregation() {
    def dsl = [where: [orderItems: ['$sum': [amount: ['$gt': 1000]]]]]
    queryAndAssertSize(order(), dsl, 0)
  }

  @Disabled('design-only: $avg 关联聚合入口未实现（v1.3 planned）')
  @Test
  @DisplayName('22-03 $avg 关联聚合')
  void test_22_03_avgRelationAggregation() {
    def dsl = [where: [reviews: ['$avg': [score: ['$gte': 4]]]]]
    queryAndAssertSize(order(), dsl, 0)
  }

  @Disabled('design-only: $max 关联聚合入口未实现（v1.3 planned）')
  @Test
  @DisplayName('22-04 $max 关联聚合')
  void test_22_04_maxRelationAggregation() {
    def dsl = [where: [orderItems: ['$max': [price: ['$gt': 500]]]]]
    queryAndAssertSize(order(), dsl, 0)
  }

  @Disabled('design-only: $min 关联聚合入口未实现（v1.3 planned）')
  @Test
  @DisplayName('22-05 $min 关联聚合')
  void test_22_05_minRelationAggregation() {
    def dsl = [where: [orderItems: ['$min': [price: ['$lt': 10]]]]]
    queryAndAssertSize(order(), dsl, 0)
  }
}
