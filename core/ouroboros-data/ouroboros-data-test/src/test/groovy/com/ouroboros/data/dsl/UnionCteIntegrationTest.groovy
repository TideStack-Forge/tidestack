package com.ouroboros.data.dsl

import static org.junit.jupiter.api.Assertions.assertTrue

import com.ouroboros.data.AbstractDslIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName('UNION / CTE 集成测试')
class UnionCteIntegrationTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
  }

  // ── Section 16: UNION / UNION ALL ──

  @Test
  @DisplayName('16-01 UNION')
  void test_16_01_union() {
    def dsl = [
      select: ['name'],
      from  : 'User',
      union : [
        [select: ['name'], from: 'User'],
        [select: ['name'], from: 'Department']
      ]
    ]

    def records = query(user(), dsl)
    assertTrue(records.size() >= 3)
  }

  @Disabled('design-only: UNION ALL 结果顺序与重复保留语义在集成层尚未冻结')
  @Test
  @DisplayName('16-02 UNION ALL')
  void test_16_02_unionAll() {
    def dsl = [
      select  : ['name'],
      from    : 'User',
      unionAll: [
        [select: ['name'], from: 'User'],
        [select: ['name'], from: 'Department']
      ]
    ]

    query(user(), dsl)
  }

  @Disabled('design-only: UNION + ORDER/LIMIT 在不同方言中的行为待统一')
  @Test
  @DisplayName('16-03 UNION + ORDER')
  void test_16_03_unionWithOrder() {
    def dsl = [
      select: ['name'],
      from  : 'User',
      union : [
        [select: ['name'], from: 'User'],
        [select: ['name'], from: 'Department']
      ],
      order : [[name: 'asc']],
      limit : 5
    ]

    query(user(), dsl)
  }

  // ── Section 17: WITH / CTE ──

  @Test
  @DisplayName('17-01 基础 CTE')
  void test_17_01_basicCte() {
    def dsl = [
      with  : [
        activeUsers: [
          select: ['id', 'name'],
          from  : 'User',
          where : [status: 'active']
        ]
      ],
      select: ['name'],
      from  : 'activeUsers'
    ]

    def records = query(user(), dsl)
    assertTrue(records.size() >= 1)
  }

  @Disabled('design-only: 多 CTE 在集成查询中的引用约束待补充')
  @Test
  @DisplayName('17-02 多个 CTE')
  void test_17_02_multipleCte() {
    def dsl = [
      with  : [
        activeUsers : [select: ['id'], from: 'User', where: [status: 'active']],
        activeOrders: [select: ['id'], from: 'Order', where: [status: 'COMPLETED']]
      ],
      select: ['id'],
      from  : 'activeUsers'
    ]

    query(user(), dsl)
  }

  @Disabled('design-only: WITH RECURSIVE 端到端执行链路待专项稳定化')
  @Test
  @DisplayName('17-03 递归 CTE')
  void test_17_03_recursiveCte() {
    def dsl = [
      withRecursive: [
        nodeTree: [
          select: ['id', 'name'],
          from  : 'Department'
        ]
      ],
      select       : ['id'],
      from         : 'nodeTree'
    ]

    query(user(), dsl)
  }
}
