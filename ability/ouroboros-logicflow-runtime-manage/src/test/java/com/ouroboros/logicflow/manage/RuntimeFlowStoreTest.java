package com.ouroboros.logicflow.manage;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Change;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Instance;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Snapshot;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.StoredOverride;
import com.ouroboros.logicflow.model.FlowModelDefinitions;
import com.ouroboros.logicflow.model.FlowModelMeta;

class RuntimeFlowStoreTest {
  @Test
  void emptyDatabaseBootAndRestartKeepTheSameEpochAndDurableDefinition() {
    var fixture = new RuntimeFlowDatabaseFixture();
    fixture.withModels(() -> {
      var first = fixture.store();
      var initial = first.initialize();
      assertTrue(initial.overrides().isEmpty());
      assertEquals(0, initial.version().revision());
      var saved = save(initial, "Welcome", "first");
      assertEquals(saved, first.commit(initial.version(), saved));
      var restarted = fixture.store();
      var read = restarted.initialize();
      assertEquals(saved.committedVersion(), read.version());
      assertEquals(saved.after(), read.overrides().get("welcome"));
      assertEquals(saved, restarted.operation(saved.operationId()).orElseThrow());
      return null;
    });
  }

  @Test
  void staleWritesRollbackAndRevokeRecreatePreserveMonotonicVersion() {
    var fixture = new RuntimeFlowDatabaseFixture();
    fixture.withModels(() -> {
      var store = fixture.store();
      var initial = store.initialize();
      var saved = save(initial, "Welcome", "first");
      store.commit(initial.version(), saved);
      var stale = save(initial, "WELCOME", "stale");
      assertThrows(BusinessException.class, () -> store.commit(initial.version(), stale));
      assertTrue(store.operation(stale.operationId()).isEmpty());
      var current = store.read();
      var tombstone = new StoredOverride("Welcome", 2, false, null, "base", "tester", Instant.now().toString());
      var revoked = new Change(UUID.randomUUID().toString(), "revoke", "tester", "revoke", "Welcome",
          saved.after(), tombstone, current.version().next(), Instant.now().toString(), true);
      store.commit(current.version(), revoked);
      assertFalse(store.read().overrides().get("welcome").active());
      assertNull(store.read().overrides().get("welcome").definition());
      var recreated = save(store.read(), "Welcome", "new");
      store.commit(store.read().version(), recreated);
      assertEquals(3, store.read().overrides().get("welcome").version());
      assertThrows(BusinessException.class, () -> store.commit(current.version(), save(current, "Welcome", "old-page")));
      return null;
    });
  }

  @Test
  void identicalOperationReplaysButActorOrPayloadReuseConflictsAndNoChangeIsRecorded() {
    var fixture = new RuntimeFlowDatabaseFixture();
    fixture.withModels(() -> {
      var store = fixture.store();
      var initial = store.initialize();
      var saved = save(initial, "one", "first");
      store.commit(initial.version(), saved);
      assertEquals(saved, store.commit(initial.version(), saved));
      var changedActor = new Change(saved.operationId(), saved.requestHash(), "other", saved.action(), saved.name(),
          saved.before(), saved.after(), saved.committedVersion(), saved.committedAt(), true);
      assertThrows(BusinessException.class, () -> store.commit(initial.version(), changedActor));
      var changedPayload = new Change(saved.operationId(), "different", saved.actor(), saved.action(), saved.name(),
          saved.before(), saved.after(), saved.committedVersion(), saved.committedAt(), true);
      assertThrows(BusinessException.class, () -> store.commit(initial.version(), changedPayload));
      var current = store.read();
      var noop = new Change(UUID.randomUUID().toString(), "noop", "tester", "save", "one", saved.after(), saved.after(),
          current.version().next(), Instant.now().toString(), false);
      store.commit(current.version(), noop);
      assertEquals(1, store.read().overrides().get("one").version());
      assertEquals(noop, store.operation(noop.operationId()).orElseThrow());
      assertEquals(noop.committedVersion(), store.read().version());
      return null;
    });
  }

  @Test
  void differentNamesStillContendOnTheSameCatalogueAndOnlyOneCommandCommits() throws Exception {
    var fixture = new RuntimeFlowDatabaseFixture();
    var snapshot = fixture.withModels(() -> fixture.store().initialize());
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var first = workers.submit(() -> fixture.withModels(() -> compete(fixture, snapshot, "one", start)));
      var second = workers.submit(() -> fixture.withModels(() -> compete(fixture, snapshot, "two", start)));
      start.countDown();
      assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    }
    fixture.withModels(() -> {
      assertEquals(1, fixture.store().read().overrides().size());
      assertEquals(1, fixture.store().read().version().revision());
      return null;
    });
  }

  @Test
  void storageFailureIsNotAnEmptyFallbackAndBootReportsCannotOverwriteAnotherBoot() {
    var fixture = new RuntimeFlowDatabaseFixture();
    fixture.withModels(() -> {
      var store = fixture.store();
      store.initialize();
      var first = instance("first", "applied");
      var restarted = instance("restart", "building");
      store.report(first); store.report(restarted); store.report(instance("first", "failed"));
      assertEquals(2, store.instances().size());
      assertEquals("building", store.instances().stream().filter(value -> value.bootId().equals("restart")).findFirst().orElseThrow().status());
      fixture.models.get("RuntimeFlowCatalog").delete("runtime-logicflows").get();
      assertThrows(BusinessException.class, store::read);
      return null;
    });
  }

  private static boolean compete(RuntimeFlowDatabaseFixture fixture, Snapshot snapshot, String name, CountDownLatch start) {
    try { assertTrue(start.await(5, TimeUnit.SECONDS)); }
    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
    try { fixture.store().commit(snapshot.version(), save(snapshot, name, name)); return true; }
    catch (BusinessException conflict) { assertTrue(conflict.getMessage().contains("已变化")); return false; }
  }

  private static Change save(Snapshot snapshot, String name, String description) {
    var before = snapshot.overrides().get(FlowModelDefinitions.nameKey(name));
    var definition = FlowModelDefinitions.write(FlowModelMeta.builder().name(name).description(description).build());
    var after = new StoredOverride(name, before == null ? 1 : before.version() + 1, true, definition,
        "base", "tester", Instant.now().toString());
    return new Change(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "tester", "save", name,
        before, after, snapshot.version().next(), Instant.now().toString(), true);
  }

  private static Instance instance(String bootId, String state) {
    return new Instance(bootId, "same-node", "epoch:2", "epoch:1", "default", "publication", state, null, Instant.now().toString());
  }
}
