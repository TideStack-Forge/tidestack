package com.ouroboros.data.dsl

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

import com.ouroboros.data.AbstractDslIntegrationTest
import com.ouroboros.data.record.Record
import com.ouroboros.data.record.RecordList
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName('POPULATE/OMIT 集成测试')
class PopulateOmitIntegrationTest extends AbstractDslIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData()
  }

  private static boolean hasKeyIgnoreCase(Map<String, ?> map, String key) {
    map.keySet().any { it.equalsIgnoreCase(key) }
  }

  private static Map<String, ?> asMap(Object value, String fieldName) {
    assertNotNull(value, "${fieldName} 不应为空")
    assertTrue(value instanceof Map, "${fieldName} 期望为 Map，实际: ${value?.getClass()?.getName()}")
    (Map<String, ?>) value
  }

  private static List<Map<String, ?>> asMapList(Object value, String fieldName) {
    assertNotNull(value, "${fieldName} 不应为空")
    assertTrue(value instanceof List, "${fieldName} 期望为 List，实际: ${value?.getClass()?.getName()}")
    (List<Map<String, ?>>) value
  }

  // ── Section 18: POPULATE（关联数据加载） ──

  @Test
  @DisplayName('18-01 单关联加载')
  void test_18_01_singleRelationPopulate() {
    def records = queryAndAssertSize(order(), [populate: ['user']], 5)
    assertTrue(records.every { it.get('user') instanceof Map })
  }

  @Test
  @DisplayName('18-02 多关联加载')
  void test_18_02_multipleRelationPopulate() {
    def records = queryAndAssertSize(order(), [populate: ['user', 'orderItems']], 5)
    assertTrue(records.every { it.get('user') instanceof Map })
    assertTrue(records.every { it.get('orderItems') instanceof List })
  }

  @Test
  @DisplayName('18-03 嵌套 populate')
  void test_18_03_nestedPopulate() {
    def records = queryAndAssertSize(order(), [populate: [user: [populate: 'department']]], 5)

    records.each { record ->
      def userMap = asMap(record.get('user'), 'user')
      assertTrue(userMap.get('department') instanceof Map)
    }
  }

  @Test
  @DisplayName('18-04 populate 与 where 组合')
  void test_18_04_populateWithWhere() {
    def dsl = [
      where   : [status: 'COMPLETED'],
      populate: ['user']
    ]

    def records = queryAndAssertSize(order(), dsl, 2)
    assertTrue(records.every { it.get('user') instanceof Map })
  }

  @Test
  @DisplayName('18-05 populate 与 select 组合')
  void test_18_05_populateWithSelect() {
    def dsl = [
      select  : ['id', 'amount'],
      populate: ['user']
    ]

    def records = queryAndAssertSize(order(), dsl, 5)
    assertTrue(records.every { it.get('user') instanceof Map })
  }

  @Test
  @DisplayName('18-06 populate 字符串形式')
  void test_18_06_populateStringForm() {
    def records = queryAndAssertSize(order(), [populate: 'user, orderItems'], 5)
    assertTrue(records.every { it.get('user') instanceof Map })
    assertTrue(records.every { it.get('orderItems') instanceof List })
  }

  @Test
  @DisplayName('18-07 populate Map 子查询配置')
  void test_18_07_populateMapWithSubQueryConfig() {
    // ⚠️ 待确认：populate 子查询参数（select/where/limit）是否全部生效，由该用例固定行为。
    def dsl = [
      where   : [id: 1L],
      populate: [user: [select: ['id', 'name'], where: [status: 'active'], limit: 10]]
    ]

    def records = queryAndAssertSize(order(), dsl, 1)
    def userMap = asMap(records.get(0).get('user'), 'user')
    assertEquals('Alice', userMap.get('name'))
  }

  @Test
  @DisplayName('18-08 populate Map 带 omit')
  void test_18_08_populateMapWithOmit() {
    def dsl = [
      where   : [id: 1L],
      populate: [user: [omit: ['email']]]
    ]

    def records = queryAndAssertSize(order(), dsl, 1)
    def userMap = asMap(records.get(0).get('user'), 'user')
    assertFalse(hasKeyIgnoreCase(userMap, 'email'))
  }

  @Test
  @DisplayName('18-09 嵌套与子查询组合')
  void test_18_09_nestedPopulateWithSubQueryCombination() {
    // ⚠️ 待确认：多层 populate 的 where 过滤是否逐层生效。
    def dsl = [
      where   : [user: [department: [code: 'TECH']]],
      populate: [user: [select: ['id', 'name'], populate: [department: [where: [code: 'TECH']]]]]
    ]

    def records = queryAndAssertSize(order(), dsl, 3)
    records.each { record ->
      def userMap = asMap(record.get('user'), 'user')
      assertTrue(userMap.get('department') instanceof Map)
    }
  }

  // ── Section 19: OMIT（字段排除） ──

  @Test
  @DisplayName('19-01 排除单字段')
  void test_19_01_omitSingleField() {
    RecordList records = queryAndAssertSize(user(), [omit: ['email']], 3)
    assertTrue(records.every { Record item -> !hasKeyIgnoreCase(item, 'email') })
  }

  @Test
  @DisplayName('19-02 排除多字段')
  void test_19_02_omitMultipleFields() {
    RecordList records = queryAndAssertSize(user(), [omit: ['email', 'status']], 3)

    assertTrue(records.every { Record item -> !hasKeyIgnoreCase(item, 'email') })
    assertTrue(records.every { Record item -> !hasKeyIgnoreCase(item, 'status') })
  }

  @Test
  @DisplayName('19-03 omit 与 select 冲突')
  void test_19_03_omitSelectConflict() {
    def dsl = [
      select: ['id', 'email'],
      omit  : ['email']
    ]

    RecordList records = queryAndAssertSize(user(), dsl, 3)
    assertTrue(records.every { Record item -> hasKeyIgnoreCase(item, 'id') })
    assertTrue(records.every { Record item -> !hasKeyIgnoreCase(item, 'email') })
  }

  @Test
  @DisplayName('19-04 omit 字符串形式')
  void test_19_04_omitStringForm() {
    RecordList records = queryAndAssertSize(user(), [omit: 'email, status'], 3)
    assertTrue(records.every { Record item -> !hasKeyIgnoreCase(item, 'email') })
    assertTrue(records.every { Record item -> !hasKeyIgnoreCase(item, 'status') })
  }
}
