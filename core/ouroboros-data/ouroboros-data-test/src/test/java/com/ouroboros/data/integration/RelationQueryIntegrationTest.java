package com.ouroboros.data.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.data.AbstractDataIntegrationTest;
import com.ouroboros.data.fixture.TestDataFixture;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;

/**
 * 关联查询集成测试
 * <p>
 * 测试 populate 语法和嵌套关联 where 条件。
 *
 * @since 1.0.0-beta.2
 */
class RelationQueryIntegrationTest extends AbstractDataIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData();
  }

  @Test
  void populate_manyToOne_userDepartment() {
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("*"));
    query.put("populate", "department");
    query.put("where", Collections.singletonMap("id", TestDataFixture.Ids.USER_ALICE));

    Try<RecordList> result = user().query(query);

    assertTrue(result.isSuccess(), "Query should succeed");
    RecordList records = result.get();
    assertEquals(1, records.size(), "Should return 1 user");

    Record userRecord = records.get(0);
    // populate 字段名使用模型定义的大小写（小写）
    assertNotNull(userRecord.get("department"), "Should populate department");

    // 验证关联数据（字段名使用模型定义的大小写）
    @SuppressWarnings("unchecked")
    Map<String, Object> dept = (Map<String, Object>) userRecord.get("department");
    assertEquals("技术部", dept.get("name"), "Department name should match");
    assertEquals("TECH", dept.get("code"), "Department code should match");
  }

  @Test
  void populate_manyToOne_orderUser() {
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("*"));
    query.put("populate", "user");
    query.put("where", Collections.singletonMap("id", TestDataFixture.Ids.ORDER_1));

    Try<RecordList> result = order().query(query);

    assertTrue(result.isSuccess(), "Query should succeed");
    RecordList records = result.get();
    assertEquals(1, records.size(), "Should return 1 order");

    Record orderRecord = records.get(0);
    assertNotNull(orderRecord.get("user"), "Should populate user");

    @SuppressWarnings("unchecked")
    Map<String, Object> user = (Map<String, Object>) orderRecord.get("user");
    assertEquals("Alice", user.get("name"), "User name should match");
  }

  @Test
  void populate_oneToMany_departmentUsers() {
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("*"));
    query.put("populate", "users");
    query.put("where", Collections.singletonMap("code", "TECH"));

    Try<RecordList> result = department().query(query);

    assertTrue(result.isSuccess(), "Query should succeed");
    RecordList records = result.get();
    assertEquals(1, records.size(), "Should return 1 department");

    Record deptRecord = records.get(0);
    assertNotNull(deptRecord.get("users"), "Should populate users");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> users = (List<Map<String, Object>>) deptRecord.get("users");
    assertEquals(2, users.size(), "TECH department should have 2 users");
  }

  @Test
  void populate_oneToMany_userOrders() {
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("*"));
    query.put("populate", "orders");
    query.put("where", Collections.singletonMap("id", TestDataFixture.Ids.USER_ALICE));

    Try<RecordList> result = user().query(query);

    assertTrue(result.isSuccess(), "Query should succeed");
    RecordList records = result.get();
    assertEquals(1, records.size(), "Should return 1 user");

    Record userRecord = records.get(0);
    assertNotNull(userRecord.get("orders"), "Should populate orders");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> orders = (List<Map<String, Object>>) userRecord.get("orders");
    assertEquals(2, orders.size(), "Alice should have 2 orders");
  }

  @Test
  void populate_nested_orderUserDepartment() {
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("*"));
    query.put("populate", Collections.singletonMap("user", Collections.singletonMap("populate", "department")));
    query.put("where", Collections.singletonMap("id", TestDataFixture.Ids.ORDER_1));

    Try<RecordList> result = order().query(query);

    assertTrue(result.isSuccess(), "Query should succeed");
    RecordList records = result.get();
    assertEquals(1, records.size(), "Should return 1 order");

    Record orderRecord = records.get(0);
    assertNotNull(orderRecord.get("user"), "Should populate user");

    @SuppressWarnings("unchecked")
    Map<String, Object> user = (Map<String, Object>) orderRecord.get("user");
    assertNotNull(user.get("department"), "Should populate nested department");

    @SuppressWarnings("unchecked")
    Map<String, Object> dept = (Map<String, Object>) user.get("department");
    assertEquals("技术部", dept.get("name"), "Nested department should match");
  }

  @Test
  void where_nestedRelation_departmentCode() {
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("*"));
    query.put("where", Collections.singletonMap("department", Collections.singletonMap("code", "TECH")));

    Try<RecordList> result = user().query(query);

    if (result.isFailure()) {
      System.err.println("=== FULL EXCEPTION STACK TRACE ===");
      result.getCause().printStackTrace(System.err);
      System.err.println("=== END STACK TRACE ===");
    }
    assertTrue(result.isSuccess(), () -> "Query should succeed, but failed with: " + result.getCause());
    RecordList records = result.get();
    assertEquals(2, records.size(), "Should return 2 users in TECH department");

    // 验证都是技术部的用户（SQL 返回的列名为大写）
    for (Record record : records) {
      assertEquals(TestDataFixture.Ids.DEPT_TECH, record.get("DEPARTMENTID"),
          "All users should belong to TECH department");
    }
  }
}
