package com.ouroboros.businessmodel.action.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.vavr.control.Try;

import com.ouroboros.businessmodel.action.BusinessActionExecutionAuditRecord;
import com.ouroboros.data.model.DataModel;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.model.DataModelMutation;

class DataModelBusinessActionExecutionAuditRecorderTest {
  private static final String MODEL_FULL_NAME = "ouroboros.BusinessActionExecutionAudit";

  @Test
  void rollbackCompletionUsesAnIndependentTransactionWhileSuccessJoinsTheBusinessTransaction() throws Exception {
    Field snapshotField = DataModelCenter.class.getDeclaredField("dataModelMap");
    snapshotField.setAccessible(true);
    Object previousSnapshot = snapshotField.get(null);
    try {
      Map<String, DataModel> models = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      models.put(MODEL_FULL_NAME, auditModel());
      snapshotField.set(null, models);

      RecordingTransactionManager transactions = new RecordingTransactionManager();
      DataModelBusinessActionExecutionAuditRecorder recorder =
          new DataModelBusinessActionExecutionAuditRecorder(transactions);

      recorder.record(record("COMMITTED"));
      recorder.record(record("ROLLED_BACK"));

      assertEquals(List.of(TransactionDefinition.PROPAGATION_REQUIRED,
          TransactionDefinition.PROPAGATION_REQUIRES_NEW), transactions.propagationBehaviors);
      assertEquals(2, transactions.commits);
    } finally {
      snapshotField.set(null, previousSnapshot);
    }
  }

  @Test
  void conditionalMutationAuditPersistsTheAffectedRowCountWithoutInventingObjectIds() throws Exception {
    Field snapshotField = DataModelCenter.class.getDeclaredField("dataModelMap");
    snapshotField.setAccessible(true);
    Object previousSnapshot = snapshotField.get(null);
    try {
      java.util.concurrent.atomic.AtomicReference<Map<String, Object>> persistedValues =
          new java.util.concurrent.atomic.AtomicReference<>();
      Map<String, DataModel> models = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      models.put(MODEL_FULL_NAME, auditModel(persistedValues));
      snapshotField.set(null, models);

      DataModelBusinessActionExecutionAuditRecorder recorder =
          new DataModelBusinessActionExecutionAuditRecorder(new RecordingTransactionManager());
      BusinessActionExecutionAuditRecord execution = withWriteSet(record("COMMITTED"), List.of(
          new DataModelMutation("sales.Order", "UPDATE", List.of(), 3)));

      recorder.record(execution);

      String writeSetJson = (String) persistedValues.get().get("writeSetJson");
      assertTrue(writeSetJson.contains("\"objectIds\":[]"), writeSetJson);
      assertTrue(writeSetJson.contains("\"affectedRows\":3"), writeSetJson);
      assertEquals("sales.Order.workflow", persistedValues.get().get("executionAuthorityName"));
    } finally {
      snapshotField.set(null, previousSnapshot);
    }
  }

  private static DataModel auditModel() {
    return auditModel(new java.util.concurrent.atomic.AtomicReference<>());
  }

  private static DataModel auditModel(java.util.concurrent.atomic.AtomicReference<Map<String, Object>> persistedValues) {
    return (DataModel) Proxy.newProxyInstance(DataModel.class.getClassLoader(), new Class<?>[]{DataModel.class},
        (proxy, method, arguments) -> {
          if ("update".equals(method.getName())) {
            if (arguments != null && arguments.length == 2 && arguments[1] instanceof Map<?, ?> values) {
              Map<String, Object> copy = new java.util.LinkedHashMap<>();
              values.forEach((key, value) -> copy.put(String.valueOf(key), value));
              persistedValues.set(copy);
            }
            return Try.success(1L);
          }
          throw new UnsupportedOperationException("Unexpected audit model call: " + method.getName());
        });
  }

  private static BusinessActionExecutionAuditRecord record(String transactionResult) {
    Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");
    return new BusinessActionExecutionAuditRecord("execution-1", null, "sales.Order", "revision-1", "submit",
        "handler:submit-handler", transactionResult, null, "test-user", null, startedAt, startedAt, List.of(), List.of(),
        "sales.Order.workflow");
  }

  private static BusinessActionExecutionAuditRecord withWriteSet(BusinessActionExecutionAuditRecord record,
                                                                   List<DataModelMutation> writeSet) {
    return new BusinessActionExecutionAuditRecord(record.executionId(), record.parentExecutionId(),
        record.modelFullName(), record.modelRevision(), record.actionName(), record.implementationRef(),
        record.transactionResult(), record.failureType(), record.actorRef(), record.target(), record.startedAt(),
        record.completedAt(), writeSet, record.eventIds(), record.executionAuthorityName());
  }

  private static final class RecordingTransactionManager implements PlatformTransactionManager {
    private final List<Integer> propagationBehaviors = new java.util.ArrayList<>();
    private int commits;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      propagationBehaviors.add(definition.getPropagationBehavior());
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      commits++;
    }

    @Override
    public void rollback(TransactionStatus status) {
      throw new AssertionError("Audit update should commit");
    }
  }
}
