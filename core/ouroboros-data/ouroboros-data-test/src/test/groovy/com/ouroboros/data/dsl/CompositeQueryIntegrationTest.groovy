package com.ouroboros.data.dsl

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

import com.ouroboros.data.AbstractDslIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName('复合查询组合集成测试')
class CompositeQueryIntegrationTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
  }

  private static boolean hasKeyIgnoreCase(Map<String, ?> map, String key) {
    map.keySet().any { it.equalsIgnoreCase(key) }
  }

  // ── Section 29: 复合查询组合 ──

  @Test
  @DisplayName('29-01 select + where + order + limit')
  void test_29_01_selectWhereOrderLimit() {
    def dsl = [
      select: ['id', 'name'],
      where : [age: ['$gte': 18]],
      order : [[name: 'asc']],
      limit : 20
    ]

    queryAndAssertSize(user(), dsl, 3)
  }

  @Test
  @DisplayName('29-02 where + group + having + order')
  void test_29_02_whereGroupHavingOrder() {
    def dsl = [
      select: ['status', [cnt: ['count', ['*']]]],
      where : [createdAt: ['$gte': '2025-01-01']],
      group : ['status'],
      having: ['>', ['count', ['*']], 1],
      order : [[cnt: 'desc']]
    ]

    def records = query(order(), dsl)
    assertTrue(records.size() >= 1)
  }

  @Test
  @DisplayName('29-03 关联条件 + 本表条件 + 分页')
  void test_29_03_relationAndLocalWithPagination() {
    def dsl = [
      where : [
        status: 'PENDING',
        user  : [name: ['$contains': 'Ali']]
      ],
      order : [[createdAt: 'desc']],
      limit : 10,
      offset: 0
    ]

    queryAndAssertSize(order(), dsl, 1)
  }

  @Test
  @DisplayName('29-04 多对一 + 一对多混合')
  void test_29_04_m2oAndO2mMixed() {
    def dsl = [
      where: [
        user      : [name: 'Alice'],
        orderItems: ['$any': [price: ['$gt': 100]]]
      ]
    ]

    queryAndAssertSize(order(), dsl, 2)
  }

  @Test
  @DisplayName('29-05 distinct + order + limit')
  void test_29_05_distinctOrderLimit() {
    def dsl = [
      select  : ['name'],
      distinct: true,
      order   : [[name: 'asc']],
      limit   : 10
    ]

    queryAndAssertSize(user(), dsl, 3)
  }

  @Test
  @DisplayName('29-06 union + order + limit')
  void test_29_06_unionOrderLimit() {
    def dsl = [
      select: ['name'],
      from  : 'User',
      union : [
        [select: ['name'], from: 'User'],
        [select: ['name'], from: 'Department']
      ],
      order : [[name: 'asc']],
      limit : 10
    ]

    def records = query(user(), dsl)
    assertTrue(records.size() >= 1)
  }

  @Test
  @DisplayName('29-07 with + 主查询 + 分页')
  void test_29_07_withMainQueryAndPagination() {
    def dsl = [
      with  : [
        activeUsers: [
          select: ['id', 'name'],
          from  : 'User',
          where : [status: 'active']
        ]
      ],
      select: ['id', 'name'],
      from  : 'activeUsers',
      limit : 10
    ]

    def records = query(user(), dsl)
    assertTrue(records.size() >= 1)
  }

  @Test
  @DisplayName('29-08 populate + omit 组合')
  void test_29_08_populateWithOmit() {
    def dsl = [
      select  : ['id', 'amount', 'status'],
      omit    : ['status'],
      populate: ['user', 'orderItems']
    ]

    def records = queryAndAssertSize(order(), dsl, 5)
    assertTrue(records.every { it.get('user') instanceof Map })
    assertTrue(records.every { it.get('orderItems') instanceof List })
    assertFalse(records.any { hasKeyIgnoreCase(it, 'status') })
  }
}
