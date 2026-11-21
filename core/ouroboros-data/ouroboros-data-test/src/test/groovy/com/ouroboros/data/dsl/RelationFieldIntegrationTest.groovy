package com.ouroboros.data.dsl

import com.ouroboros.data.AbstractDslIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName('多对一关联路径集成测试')
class RelationFieldIntegrationTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
  }

  // ── Section 20: 多对一关联路径 ──

  @Test
  @DisplayName('20-01 单层关联隐式等值')
  void test_20_01_singleRelationImplicitEquals() {
    queryAndAssertSize(order(), [where: [user: [name: 'Alice']]], 2)
  }

  @Test
  @DisplayName('20-02 单层关联比较操作符')
  void test_20_02_singleRelationWithOperator() {
    queryAndAssertSize(order(), [where: [user: [age: ['$gt': 18]]]], 5)
  }

  @Test
  @DisplayName('20-03 单层关联多字段组合')
  void test_20_03_singleRelationMultiField() {
    queryAndAssertSize(order(), [where: [user: [name: 'Alice', age: ['$gte': 18]]]], 2)
  }

  @Test
  @DisplayName('20-04 两层嵌套关联路径')
  void test_20_04_twoLevelNestedPath() {
    queryAndAssertSize(order(), [where: [user: [department: [name: '技术部']]]], 3)
  }

  @Test
  @DisplayName('20-05 关联字段 contains')
  void test_20_05_relationFieldContains() {
    queryAndAssertSize(order(), [where: [user: [name: ['$contains': 'li']]]], 4)
  }

  @Test
  @DisplayName('20-06 关联字段 in 操作符')
  void test_20_06_relationFieldInOperator() {
    queryAndAssertSize(order(), [where: [user: [status: ['$in': ['active', 'vip']]]]], 3)
  }

  @Test
  @DisplayName('20-07 关联字段 null 判断')
  void test_20_07_relationFieldNullCheck() {
    queryAndAssertSize(order(), [where: [user: [name: null]]], 0)
  }

  @Test
  @DisplayName('20-08 多个关联条件同时出现')
  void test_20_08_multipleRelationConditions() {
    def dsl = [
      where: [
        '$and': [
          [user: [name: 'Alice']],
          [user: [department: [code: 'TECH']]]
        ]
      ]
    ]

    queryAndAssertSize(order(), dsl, 2)
  }

  @Test
  @DisplayName('20-09 关联字段与本表字段组合')
  void test_20_09_relationAndLocalFieldCombination() {
    def dsl = [
      where: [
        status: 'PENDING',
        user  : [name: 'Alice']
      ]
    ]

    queryAndAssertSize(order(), dsl, 1)
  }

  @Test
  @DisplayName('20-10 不存在的关联路径')
  void test_20_10_unknownRelationPath() {
    assertQueryFails(order(), [where: [unknownRel: [name: 'x']]])
  }

  @Test
  @DisplayName('20-11 关联字段类型不匹配')
  void test_20_11_relationFieldTypeMismatch() {
    assertQueryFails(order(), [where: [user: [age: '非数字']]])
  }
}
