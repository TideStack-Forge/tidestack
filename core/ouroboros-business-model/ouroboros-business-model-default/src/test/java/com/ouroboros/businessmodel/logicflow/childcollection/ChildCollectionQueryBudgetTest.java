package com.ouroboros.businessmodel.logicflow.childcollection;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.ouroboros.data.dsl.SExpression;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.AbstractDataModel;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.data.station.DataStation;

class ChildCollectionQueryBudgetTest {

  @Test
  void replaceBudgetShouldStayConstantForOneTenAndHundredChildren() {
    for (var childCount : List.of(1, 10, 100)) {
      var order = dataModel("demo.order", field("id", "String", true));
      var line = lineModel();
      var plan = plan("lines", order, line, "id", "orderId", "lineId", Collections.emptyList());
      var rows = lineRows(childCount);
      line.nextQuery(records(rows));

      synchronizer(order, line).sync(
          ChildCollectionSyncMode.REPLACE,
          record("id", "O1"),
          record("lines", rows.stream().map(LinkedHashMap::new).collect(Collectors.toList())),
          List.of(plan)
      );

      assertEquals(1, line.queries.size(), "replace childCount=" + childCount + " unchanged rows should use snapshot only");
      assertTrue(line.updateCalls.isEmpty(), "unchanged rows should not produce updates");
      assertTrue(line.batchInsertCalls.isEmpty(), "existing unchanged rows should not produce inserts");
      assertTrue(line.deleteCalls.isEmpty(), "complete submitted collection should not produce deletes");
    }
  }

  @Test
  void nestedOwnerQueriesShouldFollowKAndKPlusOneChunkFormula() {
    assertNestedAttachmentQueryCount(3, 3, 1);
    assertNestedAttachmentQueryCount(4, 3, 2);
  }

  @Test
  void descendantCascadeShouldDiscoverEachLevelOnceAndSkipCascadeFinalReload() {
    var order = dataModel("demo.order", field("id", "String", true));
    var line = lineModel();
    var attachment = dataModel(
        "demo.orderLineAttachment",
        field("attachmentId", "String", true),
        field("lineId", "String", false),
        field("name", "String", false)
    );
    var tag = dataModel(
        "demo.orderLineAttachmentTag",
        field("tagId", "String", true),
        field("attachmentId", "String", false),
        field("name", "String", false)
    );
    var tagPlan = plan("tags", attachment, tag, "attachmentId", "attachmentId", "tagId", Collections.emptyList());
    var attachmentPlan = plan("attachments", line, attachment, "lineId", "lineId", "attachmentId", List.of(tagPlan));
    var linePlan = plan("lines", order, line, "id", "orderId", "lineId", List.of(attachmentPlan));

    line.nextQuery(records(lineRows(10)));
    line.nextQuery(RecordList.empty());
    attachment.nextQuery(records(numberedRows("attachmentId", "A", "lineId", "L", 10)));
    tag.nextQuery(records(numberedRows("tagId", "T", "attachmentId", "A", 10)));

    synchronizer(order, line, attachment, tag).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", "O1"),
        record("lines", List.of()),
        List.of(linePlan)
    );

    assertEquals(2, line.queries.size(), "current snapshot + root final reload");
    assertEquals(1, attachment.queries.size(), "cascade-only attachment layer should only discover descendants");
    assertEquals(1, tag.queries.size(), "cascade-only tag layer should only discover descendants");
    assertEquals(1, line.deleteCalls.size());
    assertEquals(1, attachment.deleteCalls.size());
    assertEquals(1, tag.deleteCalls.size());
    assertEquals(10, line.deleteCalls.get(0).size());
    assertEquals(10, attachment.deleteCalls.get(0).size());
    assertEquals(10, tag.deleteCalls.get(0).size());
  }

  @Test
  void mixedSyncShouldUseExistingUpdateAndBatchInsert() {
    var order = dataModel("demo.order", field("id", "String", true));
    var line = lineModel();
    var plan = plan("lines", order, line, "id", "orderId", "lineId", Collections.emptyList());
    line.nextQuery(records(
        record("lineId", "L1", "orderId", "O1", "name", "delete-me"),
        record("lineId", "L2", "orderId", "O1", "name", "old")));
    line.nextQuery(records(
        record("lineId", "L2", "orderId", "O1", "name", "updated-final"),
        record("lineId", "L3", "orderId", "O1", "name", "new-final")));
    line.nextBatchInsert(records(record("lineId", "L3", "orderId", "O1", "name", "new")));

    synchronizer(order, line).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", "O1"),
        record("lines", List.of(
            record("lineId", "L2", "name", "updated"),
            record("name", "new")
        )),
        List.of(plan)
    );

    assertEquals(1, line.deleteCalls.size());
    assertEquals(1, line.updateCalls.size());
    assertEquals(1, line.batchInsertCalls.size());
    assertEquals(0, line.singleInsertCalls);
  }

  private static void assertNestedAttachmentQueryCount(int lineCount, int chunkSize, int expectedAttachmentQueries) {
    var order = dataModel("demo.order", field("id", "String", true));
    var line = lineModel();
    var attachment = dataModel(
        "demo.orderLineAttachment",
        field("attachmentId", "String", true),
        field("lineId", "String", false),
        field("name", "String", false)
    );
    var attachmentPlan = plan("attachments", line, attachment, "lineId", "lineId", "attachmentId", Collections.emptyList());
    var linePlan = plan("lines", order, line, "id", "orderId", "lineId", List.of(attachmentPlan));
    var rows = lineRows(lineCount);
    line.nextQuery(records(rows));

    synchronizer(chunkSize, order, line, attachment).sync(
        ChildCollectionSyncMode.REPLACE,
        record("id", "O1"),
        record("lines", rows.stream()
            .map(row -> record("lineId", row.get("lineId"), "name", row.get("name"), "attachments", List.of()))
            .collect(Collectors.toList())),
        List.of(linePlan)
    );

    assertEquals(1, line.queries.size(), "line layer should use snapshot only when rows are unchanged");
    assertEquals(expectedAttachmentQueries, attachment.queries.size(), "attachment layer should only query existing owner chunks when unchanged");
  }

  private static ChildCollectionSynchronizer synchronizer(BudgetDataModel... models) {
    return synchronizer(1_000, models);
  }

  private static ChildCollectionSynchronizer synchronizer(int ownerQueryChunkSize, BudgetDataModel... models) {
    var modelMap = List.of(models).stream().collect(Collectors.toMap(DataModel::getFullName, model -> model));
    return new ChildCollectionSynchronizer(modelName -> Optional.ofNullable(modelMap.get(modelName)), ownerQueryChunkSize);
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

  private static BudgetDataModel lineModel() {
    return dataModel(
        "demo.orderLine",
        field("lineId", "String", true),
        field("orderId", "String", false),
        field("name", "String", false)
    );
  }

  private static BudgetDataModel dataModel(String fullName, DataModelFieldMeta... fields) {
    var meta = new DataModelMeta();
    var dot = fullName.lastIndexOf('.');
    meta.setNamespace(dot >= 0 ? fullName.substring(0, dot) : "");
    meta.setName(dot >= 0 ? fullName.substring(dot + 1) : fullName);
    meta.setFields(List.of(fields));
    meta.setPrimaryKeys(List.of(fields).stream()
        .filter(field -> field.getExtraProp(Boolean.class, "isPrimaryKey").orElse(false))
        .map(DataModelFieldMeta::getName)
        .collect(Collectors.toList()));
    return new BudgetDataModel(meta);
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

  private static List<Map<String, Object>> lineRows(int count) {
    return numberedRows("lineId", "L", "orderId", "O1", count);
  }

  private static List<Map<String, Object>> numberedRows(String primaryKeyField,
                                                        String primaryKeyPrefix,
                                                        String referenceField,
                                                        String referencePrefix,
                                                        int count) {
    var rows = new ArrayList<Map<String, Object>>();
    for (int index = 1; index <= count; index++) {
      rows.add(record(
          primaryKeyField, primaryKeyPrefix + index,
          referenceField, "O1".equals(referencePrefix) ? "O1" : referencePrefix + index,
          "name", "row-" + index
      ));
    }
    return rows;
  }

  private static RecordList records(List<Map<String, Object>> records) {
    return RecordList.of(records);
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

  private static final class BudgetDataModel extends AbstractDataModel {
    private final ArrayDeque<Try<RecordList>> queryResults = new ArrayDeque<>();
    private final ArrayDeque<Try<RecordList>> batchInsertResults = new ArrayDeque<>();
    private final List<QueryStatement> queries = new ArrayList<>();
    private final List<List<Map<String, Object>>> batchInsertCalls = new ArrayList<>();
    private final List<UpdateCall> updateCalls = new ArrayList<>();
    private final List<List<?>> deleteCalls = new ArrayList<>();
    private int singleInsertCalls;

    private BudgetDataModel(DataModelMeta meta) {
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
      singleInsertCalls++;
      return Try.failure(new UnsupportedOperationException("single insert should not be called"));
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
      return Try.failure(new UnsupportedOperationException("single update should not be called"));
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
