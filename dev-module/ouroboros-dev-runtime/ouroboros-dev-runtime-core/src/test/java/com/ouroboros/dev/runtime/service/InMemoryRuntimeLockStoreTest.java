package com.ouroboros.dev.runtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeLockStatus;

class InMemoryRuntimeLockStoreTest {

  @Test
  void shouldOverwriteLatestSuccessfulLockForSameSlot() {
    InMemoryRuntimeLockStore store = new InMemoryRuntimeLockStore();
    RuntimeLockKey slot = RuntimeLockKey.of("crm", "alice", RuntimeKind.TEST_APP);
    RuntimeLockRecord first = RuntimeLockRecord.of(slot, lock("1.0.0", Instant.parse("2026-03-24T01:00:00Z")));
    RuntimeLockRecord second = RuntimeLockRecord.of(slot, lock("1.1.0", Instant.parse("2026-03-24T02:00:00Z")));

    store.saveLatestSuccessful(first);
    store.saveLatestSuccessful(second);

    RuntimeLockRecord latest = store.findLatestSuccessful(slot).get();
    assertEquals("1.1.0", latest.getLock().getTopLevelArtifacts().get(0).getVersion());
    assertEquals(Instant.parse("2026-03-24T02:00:00Z"), latest.getSavedAt());
  }

  @Test
  void shouldKeepLatestSlotsIndependentByProfileAndRuntimeKind() {
    InMemoryRuntimeLockStore store = new InMemoryRuntimeLockStore();
    RuntimeLockKey alice = RuntimeLockKey.of("crm", "alice", RuntimeKind.TEST_APP);
    RuntimeLockKey bob = RuntimeLockKey.of("crm", "bob", RuntimeKind.TEST_APP);
    RuntimeLockKey staging = RuntimeLockKey.of("crm", "alice", RuntimeKind.DEV_HELPER);

    store.saveLatestSuccessful(RuntimeLockRecord.of(alice, lock("1.0.0", Instant.parse("2026-03-24T01:00:00Z"))));
    store.saveLatestSuccessful(RuntimeLockRecord.of(bob, lock("2.0.0", Instant.parse("2026-03-24T01:10:00Z"))));
    store.saveLatestSuccessful(RuntimeLockRecord.of(staging, lock("3.0.0", Instant.parse("2026-03-24T01:20:00Z"))));

    assertEquals("1.0.0", store.findLatestSuccessful(alice).get().getLock().getTopLevelArtifacts().get(0).getVersion());
    assertEquals("2.0.0", store.findLatestSuccessful(bob).get().getLock().getTopLevelArtifacts().get(0).getVersion());
    assertEquals("3.0.0", store.findLatestSuccessful(staging).get().getLock().getTopLevelArtifacts().get(0).getVersion());
  }

  @Test
  void shouldReturnEmptyWhenNoLatestSuccessfulExists() {
    InMemoryRuntimeLockStore store = new InMemoryRuntimeLockStore();

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
}
