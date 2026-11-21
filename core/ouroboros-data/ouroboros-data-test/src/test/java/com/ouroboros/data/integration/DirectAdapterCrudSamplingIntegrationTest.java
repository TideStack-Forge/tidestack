package com.ouroboros.data.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.ouroboros.data.AbstractDataIntegrationTest;
import com.ouroboros.data.adapter.DataAdapter;
import com.ouroboros.data.adapter.SqlDataAdapter;
import com.ouroboros.data.fixture.TestDataFixture;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.TestModels;
import com.ouroboros.data.record.RecordList;

/**
 * Direct adapter CRUD sampling integration tests.
 * <p>
 * Focuses on raw adapter entry points and complex condition combinations to
 * verify merge readiness for the refactor branch.
 */
@DisplayName("直接适配器 CRUD 抽样集成测试")
class DirectAdapterCrudSamplingIntegrationTest extends AbstractDataIntegrationTest {

  @BeforeEach
  void setUp() {
    fixture.initBaseData();
  }

  @Test
  @DisplayName("adapter.insert(Map) 应支持直接插入单条记录")
  void insertMap_shouldInsertSingleRow() {
    String suffix = String.valueOf(System.nanoTime());
    String code = "DIRECT_" + suffix;

    Map<String, Object> statement = new HashMap<>();
    statement.put("insert", TestModels.DEPARTMENT);
    statement.put("values", newDepartmentValues(suffix, code));

    String insertedId = adapter().insert(statement).get();

    assertNotNull(insertedId, "Should return generated id");
    assertEquals(1, department().query(Arrays.asList("id", "code"), newWhere("code", code)).get().size(),
        "Inserted department should be queryable");
  }

  @Test
  @DisplayName("adapter.insert(String, Map) 应支持直接插入单条记录")
  void insertEntityNameOverload_shouldInsertSingleRow() {
    String suffix = String.valueOf(System.nanoTime());
    String code = "DIRECT_NAME_" + suffix;

    String insertedId = adapter().insert(TestModels.DEPARTMENT, newDepartmentValues(suffix, code)).get();

    assertNotNull(insertedId, "Should return generated id");
    assertEquals(1, department().query(Arrays.asList("id", "code"), newWhere("code", code)).get().size(),
        "Inserted department should be queryable");
  }

  @Test
  @DisplayName("adapter.batchInsert(Map) 应支持直接批量插入")
  void batchInsertMap_shouldInsertMultipleRows() {
    String suffix = String.valueOf(System.nanoTime());
    String code1 = "BATCH_A_" + suffix;
    String code2 = "BATCH_B_" + suffix;

    Map<String, Object> statement = new HashMap<>();
    statement.put("insert", TestModels.DEPARTMENT);
    statement.put("values", Arrays.asList(
        newDepartmentValues("A-" + suffix, code1),
        newDepartmentValues("B-" + suffix, code2)
    ));

    List<String> insertedIds = adapter().batchInsert(statement).get();

    assertEquals(2, insertedIds.size(), "Should return 2 generated ids");
    assertEquals(1, department().query(Arrays.asList("id", "code"), newWhere("code", code1)).get().size(),
        "First inserted department should be queryable");
    assertEquals(1, department().query(Arrays.asList("id", "code"), newWhere("code", code2)).get().size(),
        "Second inserted department should be queryable");
  }

  @Test
  @DisplayName("adapter.batchInsert(String, List) 应支持直接批量插入")
  void batchInsertEntityNameOverload_shouldInsertMultipleRows() {
    String suffix = String.valueOf(System.nanoTime());
    String code1 = "BATCH_NAME_A_" + suffix;
    String code2 = "BATCH_NAME_B_" + suffix;

    List<String> insertedIds = adapter().batchInsert(
        TestModels.DEPARTMENT,
        Arrays.asList(
            newDepartmentValues("C-" + suffix, code1),
            newDepartmentValues("D-" + suffix, code2)
        )
    ).get();

    assertEquals(2, insertedIds.size(), "Should return 2 generated ids");
    assertEquals(1, department().query(Arrays.asList("id", "code"), newWhere("code", code1)).get().size(),
        "First inserted department should be queryable");
    assertEquals(1, department().query(Arrays.asList("id", "code"), newWhere("code", code2)).get().size(),
        "Second inserted department should be queryable");
  }

  @Test
  @DisplayName("adapter.update(Map) 应支持嵌套逻辑条件和多操作符组合")
  void updateMap_shouldSupportNestedLogicalConditions() {
    Map<String, Object> ageRange = new HashMap<>();
    ageRange.put("$gte", 28);
    ageRange.put("$lt", 36);

    Map<String, Object> active = new HashMap<>();
    active.put("status", "active");

    Map<String, Object> ageMatch = new HashMap<>();
    ageMatch.put("age", ageRange);

    Map<String, Object> bobMatch = new HashMap<>();
    bobMatch.put("name", "Bob");

    Map<String, Object> orCondition = new HashMap<>();
    orCondition.put("$or", Arrays.asList(ageMatch, bobMatch));

    Map<String, Object> where = new HashMap<>();
    where.put("$and", Arrays.asList(active, orCondition));

    Map<String, Object> set = new HashMap<>();
    set.put("status", "reviewing");

    Map<String, Object> statement = new HashMap<>();
    statement.put("update", TestModels.USER);
    statement.put("set", set);
    statement.put("where", where);

    Long affected = adapter().update(statement).get();

    assertEquals(2L, affected, "Should update Alice and Bob");
    assertEquals(2, user().query(Arrays.asList("id", "status"), newWhere("status", "reviewing")).get().size(),
        "Should have 2 reviewing users");
  }

  @Test
  @DisplayName("adapter.delete(Map) 应支持复杂 or/and 条件组合")
  void deleteMap_shouldSupportComplexLogicalConditions() {
    Map<String, Object> ratingLte2 = new HashMap<>();
    ratingLte2.put("rating", operatorMap("$lte", 2));

    Map<String, Object> ratingEq3 = new HashMap<>();
    ratingEq3.put("rating", 3);

    Map<String, Object> scoreLt35 = new HashMap<>();
    scoreLt35.put("score", operatorMap("$lt", 3.5));

    Map<String, Object> andCondition = new HashMap<>();
    andCondition.put("$and", Arrays.asList(ratingEq3, scoreLt35));

    Map<String, Object> where = new HashMap<>();
    where.put("$or", Arrays.asList(ratingLte2, andCondition));

    Map<String, Object> statement = new HashMap<>();
    statement.put("delete", TestModels.REVIEW);
    statement.put("where", where);

    Long affected = adapter().delete(statement).get();

    assertEquals(2L, affected, "Should delete review 4 and 5");
    assertEquals(3, review().query(selectAll()).get().size(), "Should keep 3 reviews");
  }

  @Test
  @DisplayName("adapter.execute(Map) 应分发 INSERT 语句")
  void executeMap_shouldDispatchInsertStatement() {
    String suffix = String.valueOf(System.nanoTime());
    String code = "FIN_" + suffix;

    Map<String, Object> values = new HashMap<>();
    values.put("name", "财务部-" + suffix);
    values.put("code", code);

    Map<String, Object> statement = new HashMap<>();
    statement.put("insert", TestModels.DEPARTMENT);
    statement.put("values", values);

    String insertedId = (String) adapter().execute(statement).get();

    assertNotNull(insertedId, "Should return generated id");
    assertEquals(1, department().query(Arrays.asList("id", "code"), newWhere("code", code)).get().size(),
        "Inserted department should be queryable");
  }

  @Test
  @DisplayName("adapter.execute(Map) 应分发 UPDATE 语句并支持 List where")
  void executeMap_shouldDispatchUpdateStatement() {
    Map<String, Object> set = new HashMap<>();
    set.put("name", "技术平台主管理部");

    List<Map<String, Object>> where = new ArrayList<>();
    where.add(newWhere("code", "TECH"));
    where.add(newWhere("id", operatorMap("$gt", 0)));

    Map<String, Object> statement = new HashMap<>();
    statement.put("update", TestModels.DEPARTMENT);
    statement.put("set", set);
    statement.put("where", where);

    Long affected = (Long) adapter().execute(statement).get();

    assertEquals(1L, affected, "Should update the TECH department");
    assertEquals("技术平台主管理部",
        department().query(Arrays.asList("id", "name"), newWhere("code", "TECH")).get().get(0).get("NAME"),
        "Department name should be updated");
  }

  @Test
  @DisplayName("adapter.execute(Map) 应分发 DELETE 语句")
  void executeMap_shouldDispatchDeleteStatement() {
    Map<String, Object> orderMatch = newWhere("orderId", TestDataFixture.Ids.ORDER_1);
    Map<String, Object> ratingMatch = newWhere("rating", operatorMap("$lte", 2));

    Map<String, Object> where = new HashMap<>();
    where.put("$and", Arrays.asList(orderMatch, ratingMatch));

    Map<String, Object> statement = new HashMap<>();
    statement.put("delete", TestModels.REVIEW);
    statement.put("where", where);

    Long affected = (Long) adapter().execute(statement).get();

    assertEquals(1L, affected, "Should delete the low-rated review of order 1");
    assertEquals(4, review().query(selectAll()).get().size(), "Should keep 4 reviews");
  }

  @Test
  @DisplayName("adapter.query/count 按实体名重载应支持复合 where")
  void queryAndCount_shouldSupportComplexConditionsViaEntityNameOverloads() {
    Map<String, Object> where = new HashMap<>();
    where.put("$and", Arrays.asList(
        newWhere("status", "active"),
        newWhere("age", operatorMap("$gte", 28, "$lt", 36))
    ));

    RecordList records = adapter().query(TestModels.USER, where).get();
    Long count = adapter().count(TestModels.USER, where).get();

    assertEquals(2, records.size(), "Should query Alice and Bob");
    assertEquals(2L, count, "Count should match query result");
  }

  @Test
  @DisplayName("adapter.query(Map) 应支持复杂 where + 排序 + 分页")
  void queryMap_shouldSupportOrderingPaginationAndCompoundWhere() {
    Map<String, Object> pending = newWhere("status", "PENDING");

    Map<String, Object> completed = newWhere("status", "COMPLETED");
    Map<String, Object> amountGte200 = newWhere("amount", operatorMap("$gte", 200));

    Map<String, Object> completedAndLarge = new HashMap<>();
    completedAndLarge.put("$and", Arrays.asList(completed, amountGte200));

    Map<String, Object> where = new HashMap<>();
    where.put("$or", Arrays.asList(pending, completedAndLarge));

    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("id", "status", "amount"));
    query.put("from", TestModels.ORDER);
    query.put("where", where);
    query.put("order", Arrays.asList(Collections.singletonMap("createdAt", "DESC")));
    query.put("limit", 2);

    RecordList result = adapter().query(query).get();

    assertEquals(2, result.size(), "Should be paginated to 2 rows");
    assertEquals(5L, ((Number) result.get(0).get("ID")).longValue(), "Newest matching order should come first");
    assertEquals(3L, ((Number) result.get(1).get("ID")).longValue(), "Second newest matching order should come second");
  }

  @Test
  @DisplayName("DataModel 批量便利接口应支持按 ID 列表更新和删除")
  void dataModelBulkConvenienceMethods_shouldWorkWithIdLists() {
    List<Long> userIds = Arrays.asList(TestDataFixture.Ids.USER_ALICE, TestDataFixture.Ids.USER_BOB);
    Map<String, Object> updateData = new HashMap<>();
    updateData.put("status", "bulk-updated");

    Long updated = user().update(userIds, updateData).get();
    Long deleted = review().delete(Arrays.asList(TestDataFixture.Ids.REVIEW_4, TestDataFixture.Ids.REVIEW_5)).get();

    assertEquals(2L, updated, "Should update 2 users by id list");
    assertEquals(2L, deleted, "Should delete 2 reviews by id list");
    assertEquals(2, user().query(Arrays.asList("id", "status"), newWhere("status", "bulk-updated")).get().size(),
        "Should query 2 bulk-updated users");
    assertEquals(3, review().query(selectAll()).get().size(), "Should keep 3 reviews after bulk delete");
  }

  @Test
  @DisplayName("直接 new SqlDataAdapter 时 DML 兼容入口应支持未注册表")
  void directSqlDataAdapter_shouldSupportDmlCompatibilityApisForUnregisteredTable() {
    String suffix = String.valueOf(System.nanoTime());
    String tableName = "t_direct_adapter_" + suffix;
    DriverManagerDataSource standaloneDataSource = new DriverManagerDataSource();
    standaloneDataSource.setDriverClassName("org.h2.Driver");
    standaloneDataSource.setUrl("jdbc:h2:mem:direct-adapter-" + suffix
        + ";MODE=MYSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
    standaloneDataSource.setUsername("sa");
    standaloneDataSource.setPassword("");

    JdbcTemplate jdbcTemplate = new JdbcTemplate(standaloneDataSource);
    jdbcTemplate.execute("CREATE TABLE " + tableName
        + " (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), code VARCHAR(50))");

    try {
      DataAdapter directAdapter = new SqlDataAdapter(standaloneDataSource);

      Map<String, Object> insertValues = new HashMap<>();
      insertValues.put("name", "raw-" + suffix);
      insertValues.put("code", "RAW_" + suffix);
      String insertedId = directAdapter.insert(tableName, insertValues).get();
      assertNotNull(insertedId, "Direct adapter should return generated id");

      Map<String, Object> updateWhere = new HashMap<>();
      updateWhere.put("code", "RAW_" + suffix);
      Map<String, Object> updateValues = new HashMap<>();
      updateValues.put("name", "updated-" + suffix);
      Long updated = directAdapter.update(tableName, updateWhere, updateValues).get();
      assertEquals(1L, updated, "Direct adapter should update the inserted row");

      Map<String, Object> deleteStatement = new HashMap<>();
      deleteStatement.put("delete", tableName);
      deleteStatement.put("where", new HashMap<String, Object>(updateWhere));
      Long deleted = (Long) directAdapter.execute(deleteStatement).get();
      assertEquals(1L, deleted, "Direct adapter should delete the inserted row via execute(Map)");

      Integer remaining = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM " + tableName + " WHERE code = ?",
          Integer.class,
          "RAW_" + suffix
      );
      assertEquals(0, remaining, "Temporary table should not retain deleted rows");
    } finally {
      jdbcTemplate.execute("DROP TABLE " + tableName);
    }
  }

  private DataAdapter adapter() {
    return user().getAdapter();
  }

  private DataModel review() {
    return getModel(TestModels.REVIEW);
  }

  private Map<String, Object> selectAll() {
    Map<String, Object> query = new HashMap<>();
    query.put("select", Arrays.asList("*"));
    return query;
  }

  private Map<String, Object> newWhere(String key, Object value) {
    Map<String, Object> where = new HashMap<>();
    where.put(key, value);
    return where;
  }

  private Map<String, Object> operatorMap(Object... pairs) {
    Map<String, Object> operators = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      operators.put((String) pairs[i], pairs[i + 1]);
    }
    return operators;
  }

  private Map<String, Object> newDepartmentValues(String suffix, String code) {
    Map<String, Object> values = new HashMap<>();
    values.put("name", "部门-" + suffix);
    values.put("code", code);
    return values;
  }
}
