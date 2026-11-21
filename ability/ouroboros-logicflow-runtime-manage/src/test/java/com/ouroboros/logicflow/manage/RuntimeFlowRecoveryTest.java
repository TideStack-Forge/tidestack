package com.ouroboros.logicflow.manage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.ouroboros.logicflow.AppLogicModelMetaProvider;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Change;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.StoredOverride;
import com.ouroboros.logicflow.manage.service.RuntimeFlowManagement.*;
import com.ouroboros.logicflow.model.FlowModelDefinitions;

class RuntimeFlowRecoveryTest {
  @TempDir Path home;
  @Test void existingInvalidModelExposesItsRuntimeDiagnosticsAndCanBeRepaired() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      Files.writeString(home.resolve("logic-flow/other.logicflow.json"),
          FlowModelDefinitions.write(RuntimeFlowFixture.parent("other", "missing")));
      new AppLogicModelMetaProvider(() -> home.toString());
      f.refresh();
      var view = f.service.read("other", RuntimeFlowManagementTest.EDITOR);
      assertEquals(Boolean.FALSE, view.valid());
      assertFalse(view.diagnostics().isEmpty());
      assertEquals(Boolean.FALSE, f.service.list("other", "all", 1, 20, RuntimeFlowManagementTest.EDITOR).items().getFirst().valid());
      f.service.save("other", new Command(UUID.randomUUID().toString(), view.editToken(),
          RuntimeFlowFixture.constant("other", "repaired")), RuntimeFlowManagementTest.EDITOR);
      f.coordinator.reconcile();
      var repaired = f.service.read("other", RuntimeFlowManagementTest.EDITOR);
      assertEquals(Boolean.TRUE, repaired.valid());
      assertTrue(repaired.diagnostics().isEmpty());
      assertEquals("repaired", f.runtime.getFlowModel("other").orElseThrow().execute().get());
    }
  }
  @Test void disabledOnlyDeploymentRemainsInspectableWithoutBecomingAnEditableOrExecutableDefault() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var disabled = RuntimeFlowFixture.constant("disabled-only", "hidden").map(builder -> builder.enabled(false));
      Files.writeString(home.resolve("logic-flow/disabled-only.logicflow.json"), FlowModelDefinitions.write(disabled));
      new AppLogicModelMetaProvider(() -> home.toString());
      f.refresh();
      var view = f.service.read("disabled-only", RuntimeFlowManagementTest.EDITOR);
      assertNull(view.defaultDefinition());
      assertFalse(view.sources().getFirst().enabled());
      assertFalse(view.sources().getFirst().selectedDefault());
      assertFalse(view.readOnlyReasons().isEmpty());
      assertFalse(view.availableActions().contains("edit"));
      assertEquals(1, f.service.list("disabled-only", "readonly", 1, 20, RuntimeFlowManagementTest.EDITOR).total());
      assertTrue(f.runtime.getFlowModel("disabled-only").isEmpty());
      RuntimeFlowManagementTest.assertCode(409, () -> f.service.save("disabled-only",
          new Command(UUID.randomUUID().toString(), view.editToken(), disabled), RuntimeFlowManagementTest.EDITOR));
    }
  }
  @Test void duplicateAndUnreadableDeploymentSourcesAreReadOnlyUntilRepaired() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var legacy = home.resolve("logic-flow/child.json");
      Files.writeString(legacy, FlowModelDefinitions.write(RuntimeFlowFixture.constant("CHILD", "legacy")));
      new AppLogicModelMetaProvider(() -> home.toString());
      f.refresh();
      var view = f.service.read("child", RuntimeFlowManagementTest.EDITOR);
      assertFalse(view.readOnlyReasons().isEmpty());
      assertFalse(view.availableActions().contains("edit"));
      assertEquals("default", f.runtime.getFlowModel("child").orElseThrow().execute().get());
      RuntimeFlowManagementTest.assertCode(409, () -> f.service.save("child",
          new Command(UUID.randomUUID().toString(), view.editToken(), RuntimeFlowFixture.constant("child", "new")),
          RuntimeFlowManagementTest.EDITOR));
      Files.delete(legacy);
      var malformed = home.resolve("logic-flow/unreadable.logicflow.json");
      Files.writeString(malformed, "{invalid-json");
      new AppLogicModelMetaProvider(() -> home.toString());
      f.refresh();
      assertFalse(f.service.read("child", RuntimeFlowManagementTest.EDITOR).readOnlyReasons().isEmpty());
      Files.delete(malformed);
      new AppLogicModelMetaProvider(() -> home.toString());
      f.refresh();
      assertTrue(f.service.read("child", RuntimeFlowManagementTest.EDITOR).availableActions().contains("edit"));
    }
  }
  @Test void invalidDurableTargetKeepsOldCompletedGenerationAndRevokeRecoversIndependently() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var before = f.store.read();
      var invalid = new StoredOverride("child", 1, true, FlowModelDefinitions.write(RuntimeFlowFixture.parent("child", "missing")),
          f.service.read("child", RuntimeFlowManagementTest.EDITOR).defaultHash(), "fixture", Instant.now().toString());
      var change = new Change(UUID.randomUUID().toString(), "invalid-fixture", "fixture", "save", "child", null,
          invalid, before.version().next(), Instant.now().toString(), true);
      f.store.commit(before.version(), change);
      var generation = f.runtime.publication().orElseThrow();
      f.coordinator.reconcile();
      assertEquals("failed", f.coordinator.localStatus().status());
      assertSame(generation, f.runtime.publication().orElseThrow());
      assertEquals("default", f.runtime.getFlowModel("parent").orElseThrow().execute("input").get());
      assertTrue(OuroborosContextRefreshLifecycle.withStableContext(() -> true));
      var view = f.service.read("child", RuntimeFlowManagementTest.EDITOR);
      f.service.revoke("child", new VersionedCommand(UUID.randomUUID().toString(), view.editToken()), RuntimeFlowManagementTest.EDITOR);
      f.coordinator.reconcile();
      assertEquals("applied", f.coordinator.localStatus().status());
      assertEquals("default", f.runtime.getFlowModel("parent").orElseThrow().execute("input").get());
    }
  }

  @Test void defaultDriftIsVisibleAndRevokeUsesTheLatestSuccessfulCompleteContext() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var original = f.service.read("child", RuntimeFlowManagementTest.EDITOR);
      f.service.save("child", new Command(UUID.randomUUID().toString(), original.editToken(), RuntimeFlowFixture.constant("child", "override")), RuntimeFlowManagementTest.EDITOR);
      f.coordinator.reconcile();
      Files.writeString(home.resolve("logic-flow/child.logicflow.json"), FlowModelDefinitions.write(RuntimeFlowFixture.constant("child", "new-default")));
      new AppLogicModelMetaProvider(() -> home.toString());
      f.refresh();
      var changed = f.service.read("child", RuntimeFlowManagementTest.EDITOR);
      assertTrue(changed.defaultChanged());
      assertEquals(1, f.service.list("", "default-changed", 1, 20, RuntimeFlowManagementTest.EDITOR).total());
      assertEquals("override", f.runtime.getFlowModel("child").orElseThrow().execute().get());
      RuntimeFlowManagementTest.assertCode(409, () -> f.service.revoke("child", new VersionedCommand(UUID.randomUUID().toString(), original.editToken()), RuntimeFlowManagementTest.EDITOR));
      f.service.revoke("child", new VersionedCommand(UUID.randomUUID().toString(), changed.editToken()), RuntimeFlowManagementTest.EDITOR);
      f.coordinator.reconcile();
      assertEquals("new-default", f.runtime.getFlowModel("parent").orElseThrow().execute("input").get());
    }
  }
}
