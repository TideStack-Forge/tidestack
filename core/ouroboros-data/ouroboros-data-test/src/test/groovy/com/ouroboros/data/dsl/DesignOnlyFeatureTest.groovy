package com.ouroboros.data.dsl

import com.ouroboros.data.AbstractDslIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName('Design-only 特性占位测试')
class DesignOnlyFeatureTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
  }

  // ── Section 25: Scenario 规则（design-only） ──

  @Disabled('design-only: PLAIN_SQL / MODEL_BASED 场景配置入口尚未实现')
  @Test
  @DisplayName('25-01 PLAIN_SQL 拒绝关联查询')
  void test_25_01_plainSqlRejectsRelationQuery() {
    query(order(), [where: [user: [name: 'Alice']]])
  }

  @Disabled('design-only: PLAIN_SQL / MODEL_BASED 场景配置入口尚未实现')
  @Test
  @DisplayName('25-02 MODEL_BASED 允许关联查询')
  void test_25_02_modelBasedAllowsRelationQuery() {
    query(order(), [where: [user: [name: 'Alice']]])
  }

  @Disabled('design-only: 聚合操作符位置校验规则未在 Analyze 层统一实现')
  @Test
  @DisplayName('25-03 聚合操作符位置限制')
  void test_25_03_aggregationOperatorPositionRestriction() {
    query(order(), [where: ['$count': ['$gt': 1]]])
  }

  @Disabled('design-only: ORDER 操作符位置限制规则未冻结')
  @Test
  @DisplayName('25-04 ORDER 操作符误用')
  void test_25_04_orderOperatorMisuse() {
    query(order(), [where: [order: [[createdAt: 'desc']]]])
  }

  // ── Appendix B: Design-only 特性汇总 ──

  @Disabled('design-only: 符号关联速记 @ * ! # + / ^ v 尚未实现')
  @Test
  @DisplayName('B-01 符号关联速记')
  void test_B_01_symbolRelationShorthand() {
    query(order(), [where: ['@user.name': 'Alice']])
  }

  @Disabled('design-only: 后缀语法糖 ?/*/# 尚未实现')
  @Test
  @DisplayName('B-02 后缀语法糖')
  void test_B_02_suffixSyntacticSugar() {
    query(order(), [where: ['name?': 'Alice']])
  }

  @Disabled('design-only: 包裹语法糖 []/{} 尚未实现')
  @Test
  @DisplayName('B-03 包裹语法糖')
  void test_B_03_wrappedSyntacticSugar() {
    query(order(), [where: [[name: 'Alice']]])
  }

  @Disabled('design-only: MATCHES / REGEXP 仅别名注册，无专用 Transpiler')
  @Test
  @DisplayName('B-04 MATCHES / REGEXP')
  void test_B_04_matchesRegexpAlias() {
    query(user(), [where: [name: ['$matches': '^A.*']]])
  }

  @Disabled('design-only: $raw 原始表达式入口未确认')
  @Test
  @DisplayName('B-05 $raw 原始表达式')
  void test_B_05_rawExpression() {
    query(user(), [where: ['$raw': 'age > 18']])
  }

  @Disabled('design-only: 关联聚合 $sum/$avg/$max/$min 在关联入口仍为 v1.3 planned')
  @Test
  @DisplayName('B-06 关联聚合入口')
  void test_B_06_relationAggregationEntry() {
    query(order(), [where: [orderItems: ['$sum': [amount: ['$gt': 1000]]]]])
  }

  @Disabled('design-only: Scenario 规则在当前实现仅文档先行')
  @Test
  @DisplayName('B-07 Scenario 规则文档先行')
  void test_B_07_scenarioRulesDocumentFirst() {
    query(order(), [where: [status: 'PENDING']])
  }
}
