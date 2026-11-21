package com.ouroboros.data.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ouroboros.data.AbstractDataIntegrationTest;
import com.ouroboros.data.fixture.TestDataFixture;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;

/**
 * 基础 CRUD 集成测试
 * <p>
 * 验证单表的 insert、update、delete 操作。
 */
@DisplayName("基础 CRUD 集成测试")
class BasicCrudIntegrationTest extends AbstractDataIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData();
  }

  @Test
  @DisplayName("insert 应该创建新记录")
  void insert_shouldCreateRecord() {
    Map<String, Object> data = new HashMap<>();
    data.put("name", "David");
    data.put("email", "david@test.com");
    data.put("departmentId", TestDataFixture.Ids.DEPT_TECH);

    Record result = user().insert(data).get();

    // H2 返回大写键名
    assertNotNull(result.get("ID"), "Should have generated ID");

    // 验证数据已插入
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("*"));
    RecordList users = user().query(query).get();
    assertEquals(4, users.size(), "Should have 4 users now");
  }

  @Test
  @DisplayName("batchInsert 应该创建多条记录")
  void batchInsert_shouldCreateRecords() {
    Map<String, Object> first = new HashMap<>();
    first.put("name", "Delta");
    first.put("email", "delta@test.com");
    first.put("departmentId", TestDataFixture.Ids.DEPT_TECH);

    Map<String, Object> second = new HashMap<>();
    second.put("name", "Echo");
    second.put("email", "echo@test.com");
    second.put("departmentId", TestDataFixture.Ids.DEPT_HR);

    RecordList inserted = user().batchInsert(Arrays.asList(first, second)).get();

    assertEquals(2, inserted.size(), "Should return 2 inserted records");

    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("*"));
    RecordList users = user().query(query).get();
    assertEquals(5, users.size(), "Should have 5 users now");
  }

  @Test
  @DisplayName("update 应该修改记录")
  void update_shouldModifyRecord() {
    Map<String, Object> where = new HashMap<>();
    where.put("id", TestDataFixture.Ids.USER_ALICE);

    Map<String, Object> updateData = new HashMap<>();
    updateData.put("name", "Alice Updated");

    Long affected = user().update(where, updateData).get();

    assertEquals(1L, affected, "Should affect 1 row");

    // 验证数据已更新; H2 返回大写键名
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("id", "name"));
    query.put("where", where);
    RecordList result = user().query(query).get();
    assertEquals("Alice Updated", result.get(0).get("NAME"), "Name should be updated");
  }

  @Test
  @DisplayName("delete 应该删除记录")
  void delete_shouldRemoveRecord() {
    // 先删除 Charlie 的订单，避免外键约束
    Map<String, Object> orderWhere = new HashMap<>();
    orderWhere.put("userId", TestDataFixture.Ids.USER_CHARLIE);
    order().delete(orderWhere).get();

    // 删除 Charlie
    Map<String, Object> where = new HashMap<>();
    where.put("id", TestDataFixture.Ids.USER_CHARLIE);

    Long affected = user().delete(where).get();

    assertEquals(1L, affected, "Should affect 1 row");

    // 验证数据已删除
    Map<String, Object> query = new HashMap<>();
    query.put("select", "*");
    RecordList result = user().query(query).get();
    assertEquals(2, result.size(), "Should have 2 users now");
  }
}
