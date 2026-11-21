package com.ouroboros.businessmodel.logicflow.childcollection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.servlet.function.ServerRequest;

import io.vavr.control.Try;

import com.ouroboros.data.adapter.DataAdapter;
import com.ouroboros.data.dsl.statement.QueryStatement;
import com.ouroboros.data.model.AbstractDataModel;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.record.Record;
import com.ouroboros.data.record.RecordList;
import com.ouroboros.data.station.DataStation;
import com.ouroboros.logicflow.adapter.HandlerFunctionAdapter;

class ChildCollectionSyncTransactionIntegrationTest {

  @Test
  void webOuterTransactionShouldRollbackMainAndChildWritesWhenNestedSyncFails() throws Exception {
    var transactionManager = new RecordingTransactionManager();
    var handler = HandlerFunctionAdapter.buildHandler("demo.order.add", transactionManager, (flowName, inputs) -> {
      try {
        transactionManager.stage("demo.order.insert:O1");

        var order = dataModel(transactionManager, "demo.order", field("id", "String", true));
        var line = dataModel(
            transactionManager,
            "demo.orderLine",
            field("lineId", "String", true),
            field("orderId", "String", false),
            field("name", "String", false)
        );
        var attachment = dataModel(
            transactionManager,
            "demo.orderLineAttachment",
            field("attachmentId", "String", true),
            field("lineId", "String", false),
            field("name", "String", false)
        );
        line.nextBatchInsert(records(record("lineId", "L1", "orderId", "O1", "name", "line")));
        attachment.failNextBatchInsert(new IllegalStateException("nested unique violation"));

        var attachmentPlan = plan("attachments", line, attachment, "lineId", "lineId", "attachmentId", Collections.emptyList());
        var linePlan = plan("lines", order, line, "id", "orderId", "lineId", List.of(attachmentPlan));
        synchronizer(order, line, attachment).sync(
            ChildCollectionSyncMode.CREATE,
            record("id", "O1"),
            record("lines", List.of(record("name", "line", "attachments", List.of(record("name", "bad"))))),
            List.of(linePlan)
        );
        return Try.success(record("id", "O1"));
      } catch (Throwable error) {
        return Try.failure(error);
      }
    });

    handler.handle(stubRequest());

    assertEquals(List.of("begin", "rollback"), transactionManager.events);
    assertTrue(transactionManager.committedWrites.isEmpty());
    assertTrue(transactionManager.stagedWrites.isEmpty());
    assertEquals(
        List.of(
            "demo.order.insert:O1",
            "demo.orderLine.batchInsert:1",
            "demo.orderLineAttachment.batchInsert:1"
        ),
        transactionManager.rolledBackWrites
    );
  }

  private static ChildCollectionSynchronizer synchronizer(TransactionalDataModel... models) {
    var modelMap = List.of(models).stream().collect(Collectors.toMap(DataModel::getFullName, model -> model));
    return new ChildCollectionSynchronizer(modelName -> Optional.ofNullable(modelMap.get(modelName)));
  }

  private static ServerRequest stubRequest() {
    return (ServerRequest) Proxy.newProxyInstance(
        ServerRequest.class.getClassLoader(),
        new Class<?>[] {ServerRequest.class},
        (proxy, method, args) -> {
          if ("servletRequest".equals(method.getName())) {
            return new org.springframework.mock.web.MockHttpServletRequest();
          }
          if ("toString".equals(method.getName())) {
            return "stub-request";
          }
          throw new UnsupportedOperationException("request should not be read by this transaction test");
        }
    );
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

  private static TransactionalDataModel dataModel(RecordingTransactionManager transactionManager,
                                                  String fullName,
                                                  DataModelFieldMeta... fields) {
    var meta = new DataModelMeta();
    var dot = fullName.lastIndexOf('.');
    meta.setNamespace(dot >= 0 ? fullName.substring(0, dot) : "");
    meta.setName(dot >= 0 ? fullName.substring(dot + 1) : fullName);
    meta.setFields(List.of(fields));
    meta.setPrimaryKeys(List.of(fields).stream()
        .filter(field -> field.getExtraProp(Boolean.class, "isPrimaryKey").orElse(false))
        .map(DataModelFieldMeta::getName)
        .collect(Collectors.toList()));
    return new TransactionalDataModel(meta, transactionManager);
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

  private static final class RecordingTransactionManager implements PlatformTransactionManager {
    private final List<String> events = new ArrayList<>();
    private final List<String> stagedWrites = new ArrayList<>();
    private final List<String> committedWrites = new ArrayList<>();
    private final List<String> rolledBackWrites = new ArrayList<>();
    private boolean active;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      events.add("begin");
      active = true;
      stagedWrites.clear();
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      events.add("commit");
      committedWrites.addAll(stagedWrites);
      stagedWrites.clear();
      active = false;
    }

    @Override
    public void rollback(TransactionStatus status) {
      events.add("rollback");
      rolledBackWrites.addAll(stagedWrites);
      stagedWrites.clear();
      active = false;
    }

    private void stage(String write) {
      if (!active) {
        throw new IllegalStateException("no active transaction");
      }
      stagedWrites.add(write);
    }
  }

  private static final class TransactionalDataModel extends AbstractDataModel {
    private final RecordingTransactionManager transactionManager;
    private final ArrayDeque<Try<RecordList>> batchInsertResults = new ArrayDeque<>();

    private TransactionalDataModel(DataModelMeta meta, RecordingTransactionManager transactionManager) {
      super(meta);
      this.transactionManager = transactionManager;
    }

    private void nextBatchInsert(RecordList records) {
      batchInsertResults.add(Try.success(records));
    }

    private void failNextBatchInsert(Throwable error) {
      batchInsertResults.add(Try.failure(error));
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
      return Try.failure(new UnsupportedOperationException("single insert should not be called"));
    }

    @Override
    public Try<RecordList> batchInsert(List<Map<String, Object>> dataList) {
      transactionManager.stage(getFullName() + ".batchInsert:" + dataList.size());
      return batchInsertResults.isEmpty()
          ? Try.success(RecordList.of(dataList))
          : batchInsertResults.removeFirst();
    }

    @Override
    public Try<Long> update(Map<String, Object> where, Map<String, Object> data) {
      return Try.failure(new UnsupportedOperationException("single update should not be called"));
    }

    @Override
    public Try<Long> delete(List<?> ids) {
      transactionManager.stage(getFullName() + ".delete:" + ids.size());
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
      return Try.success(RecordList.empty());
    }

    @Override
    public Try<RecordList> query(Map<String, Object> statement) {
      return Try.failure(new UnsupportedOperationException("query(map) should not be called"));
    }
  }
}
