package com.ouroboros.data.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.vavr.control.Try;

import com.ouroboros.data.AbstractDataIntegrationTest;
import com.ouroboros.util.Json;

@DisplayName("显式删除插件描述符集成测试")
class ExplicitDescriptorDeleteIntegrationTest extends AbstractDataIntegrationTest {

  private static final String LOGICAL_DELETE_USER = "LogicalDeleteUser";
  private static final String ARCHIVE_DELETE_USER = "ArchiveDeleteUser";
  private static final String ARCHIVE_DELETE_ROLLBACK_USER = "ArchiveDeleteRollbackUser";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("LogicalDelete descriptor 的 delete(where) 应该打标而不是物理删除")
  void logicalDeleteWhere_shouldMarkRowInsteadOfRemovingIt() {
    insertUser(LOGICAL_DELETE_USER, 101L, "Alice", "alice@test.com");

    Long affected = getModel(LOGICAL_DELETE_USER).delete(Collections.<String, Object>singletonMap("id", 101L)).get();

    assertEquals(1L, affected.longValue());
    assertEquals(1, countRows("t_soft_delete_user", "id = 101"));
    assertEquals(Boolean.TRUE, jdbcTemplate.queryForObject(
        "select is_deleted from t_soft_delete_user where id = ?",
        Boolean.class,
        101L
    ));
    assertNotNull(jdbcTemplate.queryForObject(
        "select deleted_at from t_soft_delete_user where id = ?",
        Timestamp.class,
        101L
    ));
    assertNull(jdbcTemplate.queryForObject(
        "select deleted_by from t_soft_delete_user where id = ?",
        String.class,
        101L
    ));
  }

  @Test
  @DisplayName("LogicalDelete descriptor 的 query/count 应该默认过滤已软删除数据")
  void logicalDeleteQueryAndCount_shouldExcludeDeletedRowsByDefault() {
    insertUser(LOGICAL_DELETE_USER, 111L, "Alive", "alive@test.com");
    insertUser(LOGICAL_DELETE_USER, 112L, "Deleted", "deleted@test.com");
    getModel(LOGICAL_DELETE_USER).delete(Collections.<String, Object>singletonMap("id", 112L)).get();

    Map<String, Object> statement = new HashMap<String, Object>();
    statement.put("where", new HashMap<String, Object>());

    var visibleRecords = getModel(LOGICAL_DELETE_USER).query(statement).get();
    Long visibleCount = getModel(LOGICAL_DELETE_USER).count(new HashMap<String, Object>()).get();

    assertEquals(1, visibleRecords.size());
    assertEquals(1L, visibleCount.longValue());
    assertEquals(2, countRows("t_soft_delete_user", "1 = 1"));
  }

  @Test
  @DisplayName("ArchiveDelete descriptor 的 delete(where) 应该搬运到归档后删除源记录")
  void archiveDeleteWhere_shouldMoveRowToArchiveAndRemoveSource() {
    insertUser(ARCHIVE_DELETE_USER, 201L, "Bob", "bob@test.com");

    Long affected = getModel(ARCHIVE_DELETE_USER).delete(Collections.<String, Object>singletonMap("id", 201L)).get();

    assertEquals(1L, affected.longValue());
    assertEquals(0, countRows("t_recycle_bin_user", "id = 201"));
    assertEquals(1, countRows("t_recycle_bin_user_trash", "id = 201"));

    Map<String, Object> trashRow = jdbcTemplate.queryForMap(
        "select id, name, email, source_model, source_data_station, source_primary_key, deleted_at, deleted_by, delete_operation_id " +
            "from t_recycle_bin_user_trash where id = ?",
        201L
    );
    assertEquals(201L, ((Number) trashRow.get("ID")).longValue());
    assertEquals("Bob", trashRow.get("NAME"));
    assertEquals("bob@test.com", trashRow.get("EMAIL"));
    assertEquals(ARCHIVE_DELETE_USER, trashRow.get("SOURCE_MODEL"));
    assertEquals("test", trashRow.get("SOURCE_DATA_STATION"));
    assertEquals(Json.toJsonString(Collections.<String, Object>singletonMap("id", 201L)), trashRow.get("SOURCE_PRIMARY_KEY"));
    assertNotNull(trashRow.get("DELETED_AT"));
    assertNull(trashRow.get("DELETED_BY"));
    assertNotNull(trashRow.get("DELETE_OPERATION_ID"));
  }

  @Test
  @DisplayName("ArchiveDelete descriptor 的 delete(id) 应该也走归档插件链")
  void archiveDeleteById_shouldMoveRowToArchive() {
    insertUser(ARCHIVE_DELETE_USER, 301L, "Charlie", "charlie@test.com");

    Long affected = getModel(ARCHIVE_DELETE_USER).delete(301L).get();

    assertEquals(1L, affected.longValue());
    assertEquals(0, countRows("t_recycle_bin_user", "id = 301"));
    assertEquals(1, countRows("t_recycle_bin_user_trash", "id = 301"));
  }

  @Test
  @DisplayName("ArchiveDelete descriptor 的 delete(ids) 应该批量搬运并共享一次操作 ID")
  void archiveDeleteByIds_shouldMoveRowsToArchiveInSingleOperation() {
    insertUser(ARCHIVE_DELETE_USER, 401L, "Delta", "delta@test.com");
    insertUser(ARCHIVE_DELETE_USER, 402L, "Echo", "echo@test.com");

    Long affected = getModel(ARCHIVE_DELETE_USER).delete(Arrays.<Long>asList(401L, 402L)).get();

    assertEquals(2L, affected.longValue());
    assertEquals(0, countRows("t_recycle_bin_user", "id in (401, 402)"));
    assertEquals(2, countRows("t_recycle_bin_user_trash", "id in (401, 402)"));

    List<String> operationIds = jdbcTemplate.query(
        "select distinct delete_operation_id from t_recycle_bin_user_trash where id in (?, ?) order by delete_operation_id",
        (rs, rowNum) -> rs.getString(1),
        401L,
        402L
    );
    assertEquals(1, operationIds.size());
    assertNotNull(operationIds.get(0));
  }

  @Test
  @DisplayName("归档写入失败时应该回滚并保留源记录")
  void archiveFailure_shouldRollbackAndKeepSourceRow() {
    insertUser(ARCHIVE_DELETE_ROLLBACK_USER, 501L, "Foxtrot", "foxtrot@test.com");

    Try<Long> result = getModel(ARCHIVE_DELETE_ROLLBACK_USER).delete(Collections.<String, Object>singletonMap("id", 501L));

    assertTrue(result.isFailure());
    assertEquals(1, countRows("t_recycle_bin_rollback_user", "id = 501"));
    assertEquals(0, countRows("t_recycle_bin_rollback_user_trash", "id = 501"));
  }

  private void insertUser(String modelName, Long id, String name, String email) {
    Map<String, Object> data = new HashMap<String, Object>();
    data.put("id", id);
    data.put("name", name);
    data.put("email", email);
    getModel(modelName).insert(data).get();
  }

  private int countRows(String tableName, String whereClause) {
    return jdbcTemplate.queryForObject(
        "select count(*) from " + tableName + " where " + whereClause,
        Integer.class
    ).intValue();
  }
}
