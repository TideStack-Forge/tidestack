package com.ouroboros.dev.runtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeLockStatus;

class PersistentRuntimeLockStoreTest {

  @Test
  void shouldDelegateSaveAndFindToPersistenceGateway() {
    RecordingGateway gateway = new RecordingGateway();
    PersistentRuntimeLockStore store = new PersistentRuntimeLockStore(gateway);
    RuntimeLockKey slot = RuntimeLockKey.of("crm", "alice", RuntimeKind.TEST_APP);
    RuntimeLockRecord record = RuntimeLockRecord.of(slot, lock("1.2.3", Instant.parse("2026-03-24T12:00:00Z")));

    store.saveLatestSuccessful(record);
    Optional<RuntimeLockRecord> found = store.findLatestSuccessful(slot);

    assertEquals(slot, gateway.lastSaved.getSlot());
    assertTrue(found.isPresent());
    assertEquals("1.2.3", found.get().getLock().getTopLevelArtifacts().get(0).getVersion());
    assertEquals(1, gateway.findInvocationCount);
  }

  @Test
  void shouldReturnEmptyWhenGatewayHasNoLatestRecord() {
    PersistentRuntimeLockStore store = new PersistentRuntimeLockStore(new RecordingGateway());

    assertFalse(store.findLatestSuccessful(RuntimeLockKey.of("crm", "alice", RuntimeKind.TEST_APP)).isPresent());
  }

  private ResolvedRuntimeLock lock(String version, Instant resolvedAt) {
    AppArtifactBinding artifact = AppArtifactBinding.builder()
        .appId("crm")
        .groupId("com.example")
        .artifactId("crm-runtime")
        .version(version)
        .packaging("jar")
        .enabled(true)
        .usage("runtime")
        .build();
    return ResolvedRuntimeLock.builder()
        .appId("crm")
        .profileId("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .status(RuntimeLockStatus.READY)
        .resolvedAt(resolvedAt)
        .baseBomVersion("2026.03")
        .topLevelArtifacts(Arrays.asList(artifact))
        .build();
  }

  private static class RecordingGateway implements PersistentRuntimeLockStore.PersistenceGateway {
    private RuntimeLockRecord lastSaved;
    private int findInvocationCount;

    @Override
    public Optional<RuntimeLockRecord> findLatestSuccessful(RuntimeLockKey slot) {
      findInvocationCount++;
      if (lastSaved == null || !slot.equals(lastSaved.getSlot())) {
        return Optional.empty();
      }
      return Optional.of(lastSaved);
    }

    @Override
    public void saveLatestSuccessful(RuntimeLockRecord record) {
      this.lastSaved = record;
    }
  }
}
