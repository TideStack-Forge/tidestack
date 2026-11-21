package com.ouroboros.data.dsl

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

import com.ouroboros.data.AbstractDslIntegrationTest
import com.ouroboros.data.orchestration.JoinCapabilities
import com.ouroboros.data.orchestration.NotJoinableTestCapability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName('Pairwise 组合矩阵集成测试')
class PairwiseCombinationIntegrationTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
    NotJoinableTestCapability.clear()
  }

  @AfterEach
  void tearDown() {
    NotJoinableTestCapability.clear()
  }

  // ── Section 28: Pairwise 组合矩阵 ──

  @Test
  @DisplayName('X-01 Map + $prefixed + m2o + MODEL_BASED + joinable + simple')
  void test_28_X01_mapPrefixedM2oJoinableSimple() {
    def dsl = [where: [user: [age: ['$gt': 25]]]]
    queryAndAssertSize(order(), dsl, 3)
  }

  @Test
  @DisplayName('X-02 Map + legacy + o2m(any) + withOrderLimit')
  void test_28_X02_mapLegacyO2mWithOrderLimit() {
    def dsl = [
      where: [orderItems: [any: [price: ['$gt': 100]]]],
      order: [[createdAt: 'desc']],
      limit: 2
    ]

    queryAndAssertSize(order(), dsl, 2)
  }

  @Test
  @DisplayName('X-03 Map + implicitEqual + m2o + notJoinable + populateOmit')
  void test_28_X03_mapImplicitM2oNotJoinablePopulateOmit() {
    NotJoinableTestCapability.markNotJoinable('Order', 'User')
    assertFalse(JoinCapabilities.canJoin(order(), user()).isJoinable())

    def dsl = [
      where   : [status: 'PENDING'],
      populate: ['user'],
      omit    : ['status']
    ]

    def records = query(order(), dsl)
    assertTrue(records.size() >= 1)
  }

  @Test
  @DisplayName('X-04 S-expr + $prefixed + o2m(all) + joinable + simple')
  void test_28_X04_sExprPrefixedO2mAllJoinableSimple() {
    def dsl = [where: [reviews: ['$all': [score: ['$gte': 4]]]]]
    queryAndAssertSize(order(), dsl, 3)
  }

  @Disabled('design-only: PLAIN_SQL 场景规则当前无可配置入口')
  @Test
  @DisplayName('X-05 Map + $prefixed + o2m(none) + PLAIN_SQL + joinable + simple')
  void test_28_X05_plainSqlRejectRelation() {
    def dsl = [where: [reviews: ['$none': [score: ['$lt': 3]]]]]
    query(order(), dsl)
  }

  @Test
  @DisplayName('X-06 S-expr + legacy + m2o + joinable + withOrderLimit')
  void test_28_X06_sExprLegacyM2oWithOrderLimit() {
    def dsl = [
      where: ['=', ['field', 'status'], 'PENDING'],
      order: [[createdAt: 'desc']],
      limit: 2
    ]

    def records = query(order(), dsl)
    assertTrue(records.size() <= 2)
  }

  @Test
  @DisplayName('X-07 Map + $prefixed + m2o 多跳 + joinable + simple')
  void test_28_X07_mapPrefixedM2oMultiHopSimple() {
    def dsl = [where: [user: [department: [code: 'TECH']]]]
    queryAndAssertSize(order(), dsl, 3)
  }

  @Test
  @DisplayName('X-08 Map + implicitEqual + 无关联 + PLAIN_SQL + simple')
  void test_28_X08_mapImplicitNoRelationSimple() {
    queryAndAssertSize(user(), [where: [status: 'active']], 2)
  }
}
