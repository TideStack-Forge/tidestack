package com.ouroboros.businessmodel.logicflow.childcollection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.data.adapter.DataAdapter;
import com.ouroboros.data.dsl.Operators;
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.AbstractDataModel;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.data.station.DataStation;

class ChildCollectionSynchronizerTest {

  @Test
  void createShouldInsertSubmittedChildrenWithoutSnapshotAndReuseBatchInsertReturn() {
    var order = dataModel("demo.order", field("id", "String", true));
    var line = dataModel("demo.orderLine", field("lineId", "String", true), field("orderId", "String", false), field("name", "String", false));
    var plan = plan("lines", order, line, "id", "orderId", "lineId", Collections.emptyList());

    line.nextBatchInsert(records(record("lineId", "L1", "orderId", "O1", "name", "A-returned")));

    var result = synchronizer(order, line).sync(
        ChildCollectionSyncMode.CREATE,
        record("id", "O1"),
        record("lines", List.of(record("name", "A", "orderId", "HACK"))),
        List.of(plan)
    );

    var lines = rows(result.get("lines"));
    assertEquals(List.of("L1"), values(lines, "lineId"));
    assertEquals("A-returned", lines.get(0).get("name"));

    assertEquals(1, line.batchInsertCalls.size());
    var inserted = line.batchInsertCalls.get(0);
    assertEquals("O1", inserted.get(0).get("orderId"));
    assertEquals("A", inserted.get(0).get("name"));
    assertTrue(line.deleteCalls.isEmpty());
    assertTrue(line.updateCalls.isEmpty());
    assertEquals(0, line.queries.size(), "create 应复用 batchInsert 权威返回，不读取 snapshot 或 final reload");
  }

  @Test
  void replaceShouldDiffUpdateInsertDeleteAndKeepSubmittedOrder() {
    var order = dataModel("demo.order", field("id", "String", true));
    var line = dataModel("demo.orderLine", field("lineId", "String", true), field("orderId", "String", false), field("name", "String", false));
    var plan = plan("lines", order, line, "id", "orderId", "lineId", Collections.emptyList());

    line.nextQuery(records(
        record("lineId", "L1", "orderId", "O1", "name", "delete-me"),
        record("lineId", "L2", "orderId", "O1", "name", "old")));
    line.nextQuery(records(
        record("lineId", "L2", "orderId", "O1", "name", "updated-final"),
        record("lineId", "L3", "orderId", "O1", "name", "new-final")));
    line.nextBatchInsert(records(record("lineId", "L3", "orderId", "O1", "name", "new")));

    var result = synchronizer(order, line).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", "O1"),
        record("lines", List.of(
            record("lineId", "L2", "orderId", "HACK", "name", "updated"),
            record("name", "new", "orderId", "HACK")
        )),
        List.of(plan)
    );

    var lines = rows(result.get("lines"));
    assertEquals(List.of("L2", "L3"), values(lines, "lineId"));

    assertEquals(1, line.deleteCalls.size());
    assertEquals(List.of("L1"), line.deleteCalls.get(0));

    assertEquals(1, line.updateCalls.size());
    assertEquals(Map.of("name", "updated"), line.updateCalls.get(0).data());

    assertEquals(1, line.batchInsertCalls.size());
    var inserts = line.batchInsertCalls.get(0);
    assertEquals("O1", inserts.get(0).get("orderId"));
  }

  @Test
  void replaceShouldMatchExistingChildWhenPrimaryKeyBoxedTypesDiffer() {
    var order = dataModel("demo.order", field("id", "Long", true));
    var line = dataModel("demo.orderLine",
        field("lineId", "Long", true), field("orderId", "Long", false), field("name", "String", false));
    var plan = plan("lines", order, line, "id", "orderId", "lineId", Collections.emptyList());

    // 既有子行来自数据库查询，自增主键为 Long
    line.nextQuery(records(record("lineId", 1L, "orderId", 1L, "name", "old")));
    line.nextQuery(records(record("lineId", 1L, "orderId", 1L, "name", "updated-final")));

    // 提交数据来自 JSON 反序列化，小整数为 Integer
    var result = synchronizer(order, line).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", 1L),
        record("lines", List.of(record("lineId", 1, "orderId", 1, "name", "updated"))),
        List.of(plan)
    );

    assertEquals(1, line.updateCalls.size(),
        "数值主键装箱类型不同（Integer 提交 vs Long 查询）时既有子行仍应被识别并更新");
    assertEquals(Map.of("name", "updated"), line.updateCalls.get(0).data());
    assertEquals(0, line.deleteCalls.size(),
        "既有子行已随提交返回，不应被判为待删除");
    assertEquals(List.of(1L), values(rows(result.get("lines")), "lineId"));
  }

  @Test
  void replaceShouldMatchExistingChildWhenOwnerKeyIsStringAndReferenceIsNumeric() {
    // 父主键是雪花主键（DataModel 读出为 String），子表外键是 master 关系（DataModel 原样返回 JDBC 的 Long）
    var order = dataModel("demo.order", field("id", "String", true));
    var line = dataModel("demo.orderLine",
        field("lineId", "String", true), field("orderId", "Long", false), field("name", "String", false));
    var plan = plan("lines", order, line, "id", "orderId", "lineId", Collections.emptyList());

    line.nextQuery(records(record("lineId", "L1", "orderId", 1L, "name", "old")));
    line.nextQuery(records(record("lineId", "L1", "orderId", 1L, "name", "updated-final")));

    var result = synchronizer(order, line).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", "1"),
        record("lines", List.of(record("lineId", "L1", "name", "updated"))),
        List.of(plan)
    );

    assertEquals(1, line.updateCalls.size(),
        "父主键 String 与子表外键 Long 类型不同时既有子行仍应被识别并更新");
    assertEquals(Map.of("name", "updated"), line.updateCalls.get(0).data());
    assertEquals(0, line.deleteCalls.size(), "既有子行已随提交返回，不应被判为待删除");
    assertEquals(List.of("L1"), values(rows(result.get("lines")), "lineId"));
  }

  @Test
  void nestedResultsShouldMergeWhenChildPrimaryKeyIsNumericAndOwnerKeyIsString() {
    var order = dataModel("demo.order", field("id", "String", true));
    var line = dataModel("demo.orderLine",
        field("lineId", "Long", true), field("orderId", "Long", false), field("name", "String", false));
    var attachment = dataModel("demo.orderLineAttachment",
        field("attachmentId", "String", true), field("lineId", "Long", false), field("name", "String", false));
    var attachmentPlan = plan("attachments", line, attachment, "lineId", "lineId", "attachmentId", Collections.emptyList());
    var linePlan = plan("lines", order, line, "id", "orderId", "lineId", List.of(attachmentPlan));

    line.nextQuery(records(record("lineId", 1L, "orderId", 1L, "name", "keep")));
    attachment.nextQuery(records(record("attachmentId", "A1", "lineId", 1L, "name", "old")));

    var result = synchronizer(order, line, attachment).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", "1"),
        record("lines", List.of(record("lineId", "1", "name", "keep",
            "attachments", List.of(record("attachmentId", "A1", "name", "old"))))),
        List.of(linePlan)
    );

    var lines = rows(result.get("lines"));
    assertEquals(List.of(1L), values(lines, "lineId"), "提交主键与库中主键类型不同时子行不应重复输出");
    assertEquals(List.of("A1"), values(rows(lines.get(0).get("attachments")), "attachmentId"),
        "嵌套子表结果应按归一后的主键合并回父行");
  }

  @Test
  void replaceShouldSkipMissingRelationAndDeleteAllWhenCollectionIsEmpty() {
    var order = dataModel("demo.order", field("id", "String", true));
    var line = dataModel("demo.orderLine", field("lineId", "String", true), field("orderId", "String", false), field("name", "String", false));
    var plan = plan("lines", order, line, "id", "orderId", "lineId", Collections.emptyList());

    var missingResult = synchronizer(order, line).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", "O1"),
        record("name", "submitted"),
        List.of(plan)
    );

    assertTrue(missingResult.isEmpty());
    assertTrue(line.queries.isEmpty(), "缺失 relation field 不应查询或修改子表");

    line.nextQuery(records(
        record("lineId", "L1", "orderId", "O1", "name", "delete-a"),
        record("lineId", "L2", "orderId", "O1", "name", "delete-b")));
    line.nextQuery(RecordList.empty());

    var emptyResult = synchronizer(order, line).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", "O1"),
        record("lines", List.of()),
        List.of(plan)
    );

    var lines = rows(emptyResult.get("lines"));
    assertTrue(lines.isEmpty());
    assertEquals(List.of(List.of("L1", "L2")), line.deleteCalls);
  }

  @Test
  void replaceShouldRejectSubmittedChildIdOutsideSnapshotAndDuplicateIds() {
    var order = dataModel("demo.order", field("id", "String", true));
    var line = dataModel("demo.orderLine", field("lineId", "String", true), field("orderId", "String", false), field("name", "String", false));
    var plan = plan("lines", order, line, "id", "orderId", "lineId", Collections.emptyList());

    line.nextQuery(records(record("lineId", "L1", "orderId", "O1", "name", "current")));
    assertThrows(IllegalArgumentException.class, () -> synchronizer(order, line).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", "O1"),
        record("lines", List.of(record("lineId", "L9", "name", "hijack"))),
        List.of(plan)
    ));

    var duplicateLine = dataModel("demo.orderLine", field("lineId", "String", true), field("orderId", "String", false), field("name", "String", false));
    duplicateLine.nextQuery(records(record("lineId", "L1", "orderId", "O1", "name", "current")));
    assertThrows(IllegalArgumentException.class, () -> synchronizer(order, duplicateLine).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", "O1"),
        record("lines", List.of(
            record("lineId", "L1", "name", "first"),
            record("lineId", "L1", "name", "second")
        )),
        List.of(plan("lines", order, duplicateLine, "id", "orderId", "lineId", Collections.emptyList()))
    ));
  }

  @Test
  void nestedSyncShouldQuerySnapshotsOnlyForExistingOwnersAndPropagateNewProvenance() {
    var order = dataModel("demo.order", field("id", "String", true));
    var line = dataModel("demo.orderLine", field("lineId", "String", true), field("orderId", "String", false), field("name", "String", false));
    var attachment = dataModel(
        "demo.orderLineAttachment",
        field("attachmentId", "String", true),
        field("lineId", "String", false),
        field("name", "String", false)
    );
    var attachmentPlan = plan("attachments", line, attachment, "lineId", "lineId", "attachmentId", Collections.emptyList());
    var linePlan = plan("lines", order, line, "id", "orderId", "lineId", List.of(attachmentPlan));

    line.nextQuery(records(record("lineId", "L1", "orderId", "O1", "name", "existing")));
    line.nextBatchInsert(records(record("lineId", "L2", "orderId", "O1", "name", "new-returned")));

    attachment.nextQuery(records(record("attachmentId", "A1", "lineId", "L1", "name", "old")));
    attachment.nextBatchInsert(records(record("attachmentId", "A2", "lineId", "L2", "name", "new-returned")));

    var result = synchronizer(order, line, attachment).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", "O1"),
        record("lines", List.of(
            record("lineId", "L1", "name", "existing", "attachments", List.of(record("attachmentId", "A1", "name", "old"))),
            record("name", "new", "attachments", List.of(record("name", "new")))
        )),
        List.of(linePlan)
    );

    var lines = rows(result.get("lines"));
    assertEquals(List.of("L1", "L2"), values(lines, "lineId"));
    var firstAttachments = rows(lines.get(0).get("attachments"));
    var secondAttachments = rows(lines.get(1).get("attachments"));
    assertEquals(List.of("A1"), values(firstAttachments, "attachmentId"));
    assertEquals(List.of("A2"), values(secondAttachments, "attachmentId"));

    assertFalse(attachment.queries.isEmpty());
    var firstQueryConstants = constants(attachment.queries.get(0).getWhere());
    assertTrue(firstQueryConstants.stream().anyMatch(value -> value instanceof Collection<?> values
        && values.contains("L1")
        && !values.contains("L2")));
    assertTrue(line.updateCalls.isEmpty());
    assertTrue(attachment.updateCalls.isEmpty());
    assertEquals(1, line.queries.size(), "line 层 insert-only replace 只读取 current snapshot");
    assertEquals(1, attachment.queries.size(), "attachment 层 insert-only replace 只读取 existing owner snapshot");
  }

  @Test
  void shouldFailFastWhenRuntimeFingerprintDriftsFromPlan() {
    var order = dataModel("demo.order", field("id", "String", true));
    var designLine = dataModel("demo.orderLine", field("lineId", "String", true), field("orderId", "String", false));
    var runtimeLine = dataModel("demo.orderLine", field("lineId", "String", true), field("orderId", "Integer", false));
    var plan = plan("lines", order, designLine, "id", "orderId", "lineId", Collections.emptyList());

    var error = assertThrows(IllegalStateException.class, () -> synchronizer(order, runtimeLine).sync(
        ChildCollectionSyncMode.CREATE,
        record("id", "O1"),
        record("lines", List.of(record("name", "A"))),
        List.of(plan)
    ));

    assertTrue(error.getMessage().contains("漂移"));
  }

  private static ChildCollectionSynchronizer synchronizer(RecordingDataModel... models) {
    var modelMap = List.of(models).stream().collect(Collectors.toMap(DataModel::getFullName, model -> model));
    return new ChildCollectionSynchronizer(modelName -> Optional.ofNullable(modelMap.get(modelName)));
  }

  private static ChildCollectionSyncPlan plan(String relationName,
                                              DataModel ownerModel,
                                              DataModel childModel,
                                              String ownerKey,
                                              String childReferenceField,
                                              String childPrimaryKey,
                                              List<ChildCollectionSyncPlan> children) {
    return new ChildCollectionSyncPlan(
        relationName,
        ownerModel.getFullName(),
        childModel.getFullName(),
        ownerKey,
        childReferenceField,
        childPrimaryKey,
        ChildCollectionSyncPlanCompiler.fingerprint(meta(ownerModel), ownerKey, ownerKey),
        ChildCollectionSyncPlanCompiler.fingerprint(meta(childModel), childPrimaryKey, childReferenceField),
        children
    );
  }

  private static RecordingDataModel dataModel(String fullName, DataModelFieldMeta... fields) {
    var meta = new DataModelMeta();
    var dot = fullName.lastIndexOf('.');
    meta.setNamespace(dot >= 0 ? fullName.substring(0, dot) : "");
    meta.setName(dot >= 0 ? fullName.substring(dot + 1) : fullName);
    meta.setFields(List.of(fields));
    meta.setPrimaryKeys(List.of(fields).stream()
        .filter(field -> field.getExtraProp(Boolean.class, "isPrimaryKey").orElse(false))
        .map(DataModelFieldMeta::getName)
        .collect(Collectors.toList()));
    return new RecordingDataModel(meta);
  }

  private static DataModelFieldMeta field(String name, String type, boolean primaryKey) {
    var field = new DataModelFieldMeta();
    field.setName(name);
    field.setType(type);
    field.setExtraProps(primaryKey
        ? new LinkedHashMap<>(Map.of("isPrimaryKey", Boolean.TRUE))
        : new LinkedHashMap<>());
    return field;
  }

  private static DataModelMeta meta(DataModel model) {
    var meta = new DataModelMeta();
    meta.setNamespace(model.getNamespace());
    meta.setName(model.getName());
    meta.setPrimaryKeys(model.getPrimaryKeys().stream().map(field -> field.getName()).collect(Collectors.toList()));
    meta.setFields(model.getFields().stream().map(field -> {
      var fieldMeta = new DataModelFieldMeta();
      fieldMeta.setName(field.getName());
      fieldMeta.setType(field.getType());
      fieldMeta.setExtraProps(new LinkedHashMap<>(field.getExtraProps()));
      return fieldMeta;
    }).collect(Collectors.toList()));
    return meta;
  }

  private static RecordList records(Map<String, Object>... records) {
    return RecordList.of(List.of(records));
  }

  private static Map<String, Object> record(Object... keyValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int index = 0; index < keyValues.length; index += 2) {
      map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
    }
    return map;
  }

  private static List<Object> values(List<Map<String, Object>> rows, String field) {
    return rows.stream().map(row -> row.get(field)).collect(Collectors.toList());
  }

  private static List<Map<String, Object>> rows(Object value) {
    if (!(value instanceof Collection<?> collection)) {
      throw new AssertionError("expected collection but got " + value);
    }
    var rows = new ArrayList<Map<String, Object>>();
    for (var item : collection) {
      if (!(item instanceof Map<?, ?> map)) {
        throw new AssertionError("expected row map but got " + item);
      }
      rows.add(copyRow(map));
    }
    return rows;
  }

  private static Map<String, Object> copyRow(Map<?, ?> source) {
    var row = new LinkedHashMap<String, Object>();
    source.forEach((key, value) -> row.put(String.valueOf(key), value));
    return row;
  }

  private static List<Object> constants(SExpression<?> expression) {
    var values = new ArrayList<Object>();
    expression.walk((node, context) -> {
      if (node.getOperator() == Operators.CONSTANT && !node.getParams().isEmpty()) {
        values.add(node.getParam(0));
      }
    });
    return values;
  }

  private static final class RecordingDataModel extends AbstractDataModel {
    private final ArrayDeque<Try<RecordList>> queryResults = new ArrayDeque<>();
    private final ArrayDeque<Try<RecordList>> batchInsertResults = new ArrayDeque<>();
    private final List<QueryStatement> queries = new ArrayList<>();
    private final List<List<Map<String, Object>>> batchInsertCalls = new ArrayList<>();
    private final List<UpdateCall> updateCalls = new ArrayList<>();
    private final List<List<?>> deleteCalls = new ArrayList<>();

    private RecordingDataModel(DataModelMeta meta) {
      super(meta);
    }

    private void nextQuery(RecordList records) {
      queryResults.add(Try.success(records));
    }

    private void nextBatchInsert(RecordList records) {
      batchInsertResults.add(Try.success(records));
    }

    @Override
    public DataAdapter getAdapter() {
      return null;
    }

    @Override
    public DataStation<?> getDataStation() {
      return null;
    }

    @Override
    public Try<Record> insert(Map<String, Object> data) {
      return Try.failure(new UnsupportedOperationException("insert should not be called"));
    }

    @Override
    public Try<RecordList> batchInsert(List<Map<String, Object>> dataList) {
      batchInsertCalls.add(copyDataList(dataList));
      return batchInsertResults.isEmpty()
          ? Try.success(RecordList.of(dataList))
          : batchInsertResults.removeFirst();
    }

    @Override
    public Try<Long> update(Map<String, Object> where, Map<String, Object> data) {
      return Try.failure(new UnsupportedOperationException("update should not be called"));
    }

    @Override
    public Try<Long> update(SExpression<Boolean> where, Map<String, Object> data) {
      updateCalls.add(new UpdateCall(where, new LinkedHashMap<>(data)));
      return Try.success(1L);
    }

    @Override
    public Try<Long> delete(List<?> ids) {
      deleteCalls.add(List.copyOf(ids));
      return Try.success((long) ids.size());
    }

    @Override
    public Try<Long> delete(Map<String, Object> where) {
      return Try.failure(new UnsupportedOperationException("delete(where) should not be called"));
    }

    @Override
    public Try<Long> count(Map<String, Object> where) {
      return Try.failure(new UnsupportedOperationException("count should not be called"));
    }

    @Override
    public Try<RecordList> query(QueryStatement statement) {
      queries.add(statement);
      return queryResults.isEmpty() ? Try.success(RecordList.empty()) : queryResults.removeFirst();
    }

    @Override
    public Try<RecordList> query(Map<String, Object> statement) {
      return Try.failure(new UnsupportedOperationException("query(map) should not be called"));
    }

    private static List<Map<String, Object>> copyDataList(List<Map<String, Object>> dataList) {
      return dataList.stream()
          .map(LinkedHashMap::new)
          .collect(Collectors.toList());
    }
  }

  private record UpdateCall(SExpression<Boolean> where, Map<String, Object> data) {
  }
}
