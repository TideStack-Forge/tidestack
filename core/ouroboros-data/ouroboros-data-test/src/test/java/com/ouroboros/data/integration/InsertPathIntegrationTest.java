package com.ouroboros.data.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.AbstractDataIntegrationTest;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;

@DisplayName("插入路径集成测试")
class InsertPathIntegrationTest extends AbstractDataIntegrationTest {

  @Test
  @DisplayName("主模型插入应该成功持久化记录")
  void insert_shouldPersistDepartmentRecord() {
    Map<String, Object> data = new HashMap<>();
    data.put("id", 100L);
    data.put("name", "质量保障部");
    data.put("code", "QA");

    Record inserted = department().insert(data).get();

    assertNotNull(inserted, "insert should return the persisted record");

    Map<String, Object> where = new HashMap<>();
    where.put("id", 100L);
    Map<String, Object> query = new HashMap<>();
    query.put("where", where);
    RecordList result = department().query(query).get();
    assertEquals(1, result.size(), "department row should be persisted");
    assertEquals("质量保障部", result.get(0).get("NAME"), "inserted department name should match");
  }
}
