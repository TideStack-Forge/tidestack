package com.ouroboros.data.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.AbstractDataIntegrationTest;
import com.ouroboros.data.record.RecordList;

/**
 * 基础查询集成测试
 * <p>
 * 验证 select、where、orderBy、page 等基础查询功能。
 */
@DisplayName("基础查询集成测试")
class BasicQueryIntegrationTest extends AbstractDataIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData();
  }

  @Test
  @DisplayName("查询所有记录应该返回全部数据")
  void selectAll_shouldReturnAllRecords() {
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("*"));

    RecordList result = department().query(query).get();

    assertEquals(2, result.size(), "Should return 2 departments");
  }

  @Test
  @DisplayName("带 where 条件的查询应该正确过滤")
  void selectWithWhere_shouldFilterRecords() {
    Map<String, Object> where = new HashMap<>();
    where.put("code", "TECH");

    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("id", "name", "code"));
    query.put("where", where);

    RecordList result = department().query(query).get();

    assertEquals(1, result.size(), "Should return 1 department");
    // H2 返回大写键名
    assertEquals("技术部", result.get(0).get("NAME"), "Should be Tech department");
  }

  @Test
  @DisplayName("带 orderBy 的查询应该正确排序")
  void selectWithOrderBy_shouldSortRecords() {
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("id", "name", "code"));
    query.put("order", Arrays.asList(Collections.singletonMap("id", "DESC")));

    RecordList result = department().query(query).get();

    assertEquals(2, result.size(), "Should return 2 departments");
    assertEquals(2L, ((Number) result.get(0).get("ID")).longValue(), "First should be id=2 (DESC order)");
    assertEquals("人力资源部", result.get(0).get("NAME"), "First should be HR (DESC order by id)");
  }

  @Test
  @DisplayName("分页查询应该正确分页")
  void selectWithPage_shouldPaginate() {
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("*"));
    query.put("limit", 2);

    RecordList result = user().query(query).get();

    assertEquals(2, result.size(), "Should return 2 users (limit)");
  }
}
