package com.ouroboros.data.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DataInitializationReconcilerTest {

  @Test
  void insertsMissingRows() {
    var target = new InMemoryTargetDatasetGateway();
    var asset = asset(block(Map.of("code", "enabled", "label", "Enabled")));

    var result = reconciler(asset, target, new InMemoryLedger()).reconcile();

    assertEquals(1, result.inserted());
    assertEquals(0, result.skipped());
    assertEquals("Enabled", target.rows.get("Status").get(0).get("label"));
  }

  @Test
  void rerunFillsMissingRowsAfterPreviousSuccess() {
    var target = new InMemoryTargetDatasetGateway();
    var ledger = new InMemoryLedger();
    var asset = asset(block(Map.of("code", "enabled", "label", "Enabled")));
    reconciler(asset, target, ledger).reconcile();
    target.rows.clear();

    var result = reconciler(asset, target, ledger).reconcile();

    assertEquals(1, result.inserted());
    assertEquals(0, result.skipped());
    assertEquals("Enabled", target.rows.get("Status").get(0).get("label"));
  }

  @Test
  void skipsExistingRowsEvenWhenLedgerHasNoSuccessRecord() {
    var target = new InMemoryTargetDatasetGateway();
    target.rows.put("Status", new ArrayList<>(List.of(new HashMap<>(Map.of("code", "enabled", "label", "User Label")))));
    var asset = asset(block(Map.of("code", "enabled", "label", "Enabled")));

    var result = reconciler(asset, target, new InMemoryLedger()).reconcile();

    assertEquals(0, result.inserted());
    assertEquals(1, result.skipped());
    assertEquals("User Label", target.rows.get("Status").get(0).get("label"));
  }

  @Test
  void failsAndMarksLedgerFailureWhenRecordMissesMatchField() {
    var target = new InMemoryTargetDatasetGateway();
    var ledger = new InMemoryLedger();
    var asset = asset(block(Map.of("label", "Enabled")));

    assertThrows(IllegalArgumentException.class, () -> reconciler(asset, target, ledger).reconcile());

    assertEquals(1, ledger.failed.size());
    assertTrue(target.rows.isEmpty());
  }

  @Test
  void appliesBlockInsideTransactionBoundary() {
    var target = new InMemoryTargetDatasetGateway();
    var transaction = new RecordingTransaction();
    var asset = asset(block(Map.of("code", "enabled", "label", "Enabled")));

    new DataInitializationReconciler(List.of(() -> List.of(asset)), target, new InMemoryLedger(),
        new NoOpInitializationLock(), transaction).reconcile();

    assertEquals(1, transaction.executions);
    assertEquals(1, target.rows.get("Status").size());
  }

  @Test
  void marksLedgerSucceededInsideTransactionBoundary() {
    var target = new InMemoryTargetDatasetGateway();
    var transaction = new RecordingTransaction();
    var ledger = new TransactionAwareLedger(transaction);
    var asset = asset(block(Map.of("code", "enabled", "label", "Enabled")));

    new DataInitializationReconciler(List.of(() -> List.of(asset)), target, ledger,
        new NoOpInitializationLock(), transaction).reconcile();

    assertTrue(ledger.succeededInsideTransaction);
  }

  private DataInitializationReconciler reconciler(DataInitializationAsset asset, InMemoryTargetDatasetGateway target,
                                                  InMemoryLedger ledger) {
    return new DataInitializationReconciler(List.of(() -> List.of(asset)), target, ledger, new NoOpInitializationLock());
  }

  private DataInitializationAsset asset(InitializationBlock block) {
    return new DataInitializationAsset("classpath", "META-INF/ouroboros/metadata/status.data-init.json",
        new DataInitializationPlan("system-status", "v1", List.of(block)));
  }

  private InitializationBlock block(Map<String, Object> record) {
    return new InitializationBlock("statuses", "Status", null, List.of("code"), List.of(record));
  }

  private static final class InMemoryTargetDatasetGateway implements TargetDatasetGateway {
    private final Map<String, List<Map<String, Object>>> rows = new HashMap<>();

    @Override
    public Optional<TargetDatasetRow> findOne(String targetModel, Map<String, Object> matchValues) {
      return rows.getOrDefault(targetModel, List.of()).stream()
          .filter(row -> matchValues.entrySet().stream().allMatch(entry -> entry.getValue().equals(row.get(entry.getKey()))))
          .findFirst()
          .map(TargetDatasetRow::new);
    }

    @Override
    public void insert(String targetModel, Map<String, Object> values) {
      rows.computeIfAbsent(targetModel, ignored -> new ArrayList<>()).add(new HashMap<>(values));
    }
  }

  private static class InMemoryLedger implements InitializationLedger {
    private final List<InitializationRun> failed = new ArrayList<>();

    @Override
    public InitializationRun begin(DataInitializationAsset asset, InitializationBlock block, LedgerKey key) {
      return new InitializationRun(UUID.randomUUID().toString(), key);
    }

    @Override
    public void succeed(InitializationRun run, InitializationBlockSummary summary) {
    }

    @Override
    public void fail(InitializationRun run, Throwable cause) {
      failed.add(run);
    }
  }

  private static final class RecordingTransaction implements InitializationTransaction {
    private int executions;
    private boolean active;

    @Override
    public <T> T execute(java.util.function.Supplier<T> action) {
      executions++;
      active = true;
      try {
        return action.get();
      } finally {
        active = false;
      }
    }

    private boolean isActive() {
      return active;
    }
  }

  private static final class TransactionAwareLedger extends InMemoryLedger {
    private final RecordingTransaction transaction;
    private boolean succeededInsideTransaction;

    private TransactionAwareLedger(RecordingTransaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public void succeed(InitializationRun run, InitializationBlockSummary summary) {
      succeededInsideTransaction = transaction.isActive();
      super.succeed(run, summary);
    }
  }
}
