package com.ouroboros.logicflow.manage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.manage.service.RuntimeFlowManagement.*;
import com.ouroboros.logicflow.model.FlowModelDefinitions;

class RuntimeFlowManagementTest {
  @TempDir Path home;
  static final Access EDITOR = new Access("operator", true, true);
  static final Access READER = new Access("reader", false, false);
  static String id() { return UUID.randomUUID().toString(); }

  @Test void operationsViewReportsRecentDivergenceAndScopedHistoryIncludingLegacyRows() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var saved = f.service.save("child", new Command(id(), f.service.read("child", EDITOR).editToken(), RuntimeFlowFixture.constant("child", "new")), EDITOR);
      f.service.save("other", new Command(id(), f.service.read("other", EDITOR).editToken(), RuntimeFlowFixture.constant("other", "other-new")), EDITOR);
      var legacy = new com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Change(id(), "legacy-hash", "legacy-actor", "save", "CHILD", null, saved.after(), saved.committedVersion(), "2020-01-01T00:00:00Z", true);
      com.ouroboros.data.model.TypedDataModelCenter.getDataModel(String.class, com.ouroboros.logicflow.manage.model.RuntimeFlowChange.class).orElseThrow()
          .insert(new com.ouroboros.logicflow.manage.model.RuntimeFlowChange().setId(legacy.operationId()).setPayload(com.ouroboros.util.Json.toMap(com.ouroboros.util.Json.toJsonString(legacy)))).get();
      var current = f.store.read().version().token();
      var fingerprint = f.runtime.publication().orElseThrow().catalogue().defaults().stamp();
      var now = java.time.Instant.now().toString();
      f.store.report(new com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Instance("good", "node", current, current, fingerprint, "publication", "applied", null, now));
      f.store.report(new com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Instance("failed", "node", current, "old", "other-default", "publication", "failed", "fixture failure", now));
      f.store.report(new com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Instance("old", "node", current, current, fingerprint, "publication", "applied", null, "2020-01-01T00:00:00Z"));
      var operations = f.service.operations("CHILD", EDITOR);
      assertEquals("pending", operations.state());
      assertEquals(2, operations.changes().size());
      assertEquals(saved.operationId(), operations.changes().getFirst().operationId());
      assertEquals(legacy.operationId(), operations.changes().getLast().operationId());
      assertEquals("applied", operations.instances().stream().filter(value -> value.instance().bootId().equals("good")).findFirst().orElseThrow().state());
      var failed = operations.instances().stream().filter(value -> value.instance().bootId().equals("failed")).findFirst().orElseThrow();
      assertEquals("failed", failed.state()); assertTrue(failed.defaultDiffers());
      assertEquals("stale", operations.instances().stream().filter(value -> value.instance().bootId().equals("old")).findFirst().orElseThrow().state());
    }
  }

  @Test void saveChecksThenPublishesParentsTogetherAndRevokeAndRestoreRemainVersioned() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var oldParent = f.runtime.getFlowModel("parent").orElseThrow();
      var view = f.service.read("child", EDITOR);
      assertTrue(view.availableActions().contains("edit"), view.readOnlyReasons().toString());
      assertNotNull(view.definition());
      var definition = RuntimeFlowFixture.constant("child", "override");
      var check = f.service.validate("child", new ValidationCommand(view.editToken(), definition), EDITOR);
      assertTrue(check.valid(), check.report().toString());
      assertTrue(check.impact().contains("parent"));
      assertFalse(check.differences().isEmpty());
      assertEquals(0, f.store.read().version().revision());
      var command = new Command(id(), view.editToken(), definition);
      var saved = f.service.save("child", command, EDITOR);
      assertTrue(saved.changed());
      assertEquals("default", oldParent.execute("input").get());
      assertEquals("pending", f.service.list("child", "overridden", 1, 10, EDITOR).items().getFirst().state());
      f.coordinator.reconcile();
      assertEquals("applied", f.coordinator.localStatus().status());
      assertEquals("override", f.runtime.getFlowModel("parent").orElseThrow().execute("input").get());
      assertEquals("default", oldParent.execute("input").get());
      assertEquals(saved, f.service.save("child", command, EDITOR));
      assertEquals("applied", f.service.list("child", "all", 1, 10, EDITOR).items().getFirst().state());
      var after = f.service.read("child", EDITOR);
      var noop = f.service.save("child", new Command(id(), after.editToken(), definition), EDITOR);
      assertFalse(noop.changed());
      assertEquals(1, noop.after().version());
      f.coordinator.reconcile();
      var revoke = f.service.revoke("child", new VersionedCommand(id(), f.service.read("child", EDITOR).editToken()), EDITOR);
      assertFalse(revoke.after().active());
      f.coordinator.reconcile();
      assertEquals("default", f.runtime.getFlowModel("parent").orElseThrow().execute("input").get());
      var restored = f.service.restore("child", new RestoreCommand(id(), f.service.read("child", EDITOR).editToken(), revoke.operationId()), EDITOR);
      assertTrue(restored.after().active());
      f.coordinator.reconcile();
      assertEquals("override", f.runtime.getFlowModel("parent").orElseThrow().execute("input").get());
      var operation = f.service.operation(saved.operationId(), READER);
      assertTrue(operation.superseded());
      assertEquals(1, operation.instances().size());
      assertEquals(3, restored.after().version());
    }
  }

  @Test void staleCommandsAndCrossModelRestoreAndInvalidReferencesCannotCommit() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var view = f.service.read("child", EDITOR);
      var other = f.service.read("other", EDITOR);
      var saved = f.service.save("child", new Command(id(), view.editToken(), RuntimeFlowFixture.constant("child", "new")), EDITOR);
      assertCode(409, () -> f.service.save("other", new Command(id(), other.editToken(), RuntimeFlowFixture.constant("other", "stale")), EDITOR));
      assertCode(400, () -> f.service.restore("other", new RestoreCommand(id(), f.service.read("other", EDITOR).editToken(), saved.operationId()), EDITOR));
      var current = f.service.read("child", EDITOR);
      var cyclic = RuntimeFlowFixture.parent("child", "parent");
      assertFalse(f.service.validate("child", new ValidationCommand(current.editToken(), cyclic), EDITOR).valid());
      assertCode(422, () -> f.service.save("child", new Command(id(), current.editToken(), cyclic), EDITOR));
      var missing = RuntimeFlowFixture.parent("child", "not-deployed");
      assertFalse(f.service.validate("child", new ValidationCommand(current.editToken(), missing), EDITOR).valid());
      assertEquals(1, f.store.read().version().revision());
      assertCode(409, () -> f.service.requestApply("stale", EDITOR));
      assertEquals("pending", f.service.requestApply(f.store.read().version().token(), EDITOR).status());
    }
  }

  @Test void identitiesContractsLimitsAndQueriesAreEnforcedAndReadDoesNotBuildACanvas() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var view = f.service.read("child", READER);
      assertTrue(view.availableActions().isEmpty());
      assertCode(403, () -> f.service.save("child", new Command(id(), view.editToken(), RuntimeFlowFixture.constant("child", "x")), READER));
      assertCode(403, () -> f.service.requestApply(view.catalogVersion(), READER));
      assertCode(404, () -> f.service.read("absent", READER));
      assertCode(400, () -> f.service.read("", READER));
      assertCode(400, () -> f.service.list("", "all", 0, 20, READER));
      assertCode(400, () -> f.service.list("", "invalid", 1, 20, READER));
      assertEquals(3, f.service.list("", "all", 1, 1, READER).total());
      assertEquals(1, f.service.list("parent", "all", 1, 20, READER).items().size());
      assertEquals(0, f.service.list("", "readonly", 1, 20, READER).total());
      var changedContract = RuntimeFlowFixture.constant("child", "x").map(builder -> builder.inputDataType("string"));
      assertCode(400, () -> f.service.save("child", new Command(id(), view.editToken(), changedContract), EDITOR));
      assertCode(400, () -> f.service.save("child", new Command("invalid", view.editToken(), null), EDITOR));
      assertCode(400, () -> f.service.save("child", new Command(id(), view.editToken(), RuntimeFlowFixture.constant("renamed", "x")), EDITOR));
      assertCode(400, () -> f.service.save("child", new Command(id(), view.editToken(), RuntimeFlowFixture.constant("child", "x".repeat(2_000_001))), EDITOR));
      assertEquals(0, f.store.read().version().revision());
    }
  }

  static void assertCode(int code, org.junit.jupiter.api.function.Executable command) {
    assertEquals(code, assertThrows(BusinessException.class, command).getErrorCode());
  }
}
