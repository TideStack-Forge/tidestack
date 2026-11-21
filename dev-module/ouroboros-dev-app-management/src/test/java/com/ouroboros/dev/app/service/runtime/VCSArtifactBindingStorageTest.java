package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.AppArtifactBinding;
import com.ouroboros.dev.runtime.model.ResolvedRuntimeLock;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeLockStatus;
import com.ouroboros.dev.runtime.service.RuntimeLockKey;
import com.ouroboros.dev.runtime.service.RuntimeLockRecord;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction.CreateFileAction;
import com.ouroboros.vcs.action.VCSAction.UpdateFileAction;
import com.ouroboros.vcs.file.VCSFile;

@ExtendWith(MockitoExtension.class)
class VCSArtifactBindingStorageTest {

  @Mock
  private VCSManagerCenter vcsManagerCenter;
  @Mock
  private VCSManager vcsManager;
  @Mock
  private VCSFile vcsFile;
  @Mock
  private CreateFileAction createFileAction;
  @Mock
  private UpdateFileAction updateFileAction;

  private VCSArtifactBindingStorage storage;

  @BeforeEach
  void setUp() {
    storage = new VCSArtifactBindingStorage(vcsManagerCenter);
  }

  @Test
  void shouldGenerateDeterministicAndCollisionResistantProfileStorageKeys() {
    assertEquals(storage.profileStorageKey("a/b"), storage.profileStorageKey("a/b"));
    assertNotEquals(storage.profileStorageKey("a/b"), storage.profileStorageKey("a_b"));
  }

  @Test
  void shouldWriteUniqueResolveTriggerEvents() {
    stubCreateOnlyJsonWrites();

    Try<Void> first = storage.markResolveRequested("crm", "alice/dev", RuntimeKind.TEST_APP);
    Try<Void> second = storage.markResolveRequested("crm", "alice/dev", RuntimeKind.TEST_APP);

    assertTrue(first.isSuccess());
    assertTrue(second.isSuccess());
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
    verify(vcsManager, times(2)).createFile(pathCaptor.capture());
    verify(createFileAction, times(2)).content(contentCaptor.capture());
    List<String> paths = pathCaptor.getAllValues();
    List<String> payloads = contentCaptor.getAllValues();
    assertEquals(2, paths.size());
    assertNotEquals(paths.get(0), paths.get(1));
    assertTrue(paths.get(0).contains("runtime-lock/requests/"));
    assertTrue(paths.get(0).contains("/TEST_APP/"));
    assertTrue(payloads.get(0).contains("\"profileId\" : \"alice/dev\""));
    assertTrue(payloads.get(0).contains("\"runtimeKind\" : \"TEST_APP\""));
    assertTrue(payloads.get(0).contains("\"requestId\""));
    assertTrue(payloads.get(0).contains("\"requestedAt\""));
  }

  @Test
  void shouldPersistDistinctProfileOverlayPathsForPreviouslyCollidingIds() {
    stubCreateOrUpdateJsonWrites();

    Try<Void> first = storage.saveProfileArtifactOverlays("crm", "a/b", Collections.emptyList());
    Try<Void> second = storage.saveProfileArtifactOverlays("crm", "a_b", Collections.emptyList());

    assertTrue(first.isSuccess());
    assertTrue(second.isSuccess());
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(vcsManager, times(2)).createFile(pathCaptor.capture());
    List<String> paths = pathCaptor.getAllValues();
    assertEquals(2, paths.size());
    assertNotEquals(paths.get(0), paths.get(1));
    assertTrue(paths.get(0).endsWith("/artifact-overlays.json"));
    assertTrue(paths.get(1).endsWith("/artifact-overlays.json"));
  }

  @Test
  void shouldPersistLatestRuntimeLockToDeterministicSlotPathAndOverwrite() {
    stubCreateOrUpdateJsonWrites();
    when(vcsFile.exists()).thenReturn(false, true);
    when(vcsManager.updateFile(anyString())).thenReturn(updateFileAction);
    when(updateFileAction.content(anyString())).thenReturn(updateFileAction);
    when(updateFileAction.unlockWhenCommit()).thenReturn(updateFileAction);
    when(updateFileAction.commit(anyString())).thenReturn(Try.success(Boolean.TRUE));
    RuntimeLockKey slot = RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP);
    RuntimeLockRecord first = RuntimeLockRecord.of(slot, lock("1.0.0"));
    RuntimeLockRecord second = RuntimeLockRecord.of(slot, lock("1.1.0"));

    storage.saveLatestSuccessful(first);
    storage.saveLatestSuccessful(second);
    ArgumentCaptor<String> createPathCaptor = ArgumentCaptor.forClass(String.class);
    verify(vcsManager, times(1)).createFile(createPathCaptor.capture());
    ArgumentCaptor<String> updatePathCaptor = ArgumentCaptor.forClass(String.class);
    verify(vcsManager, times(1)).updateFile(updatePathCaptor.capture());
    assertEquals(createPathCaptor.getValue(), updatePathCaptor.getValue());
    assertTrue(createPathCaptor.getValue().contains("runtime-lock/latest/"));
    assertTrue(createPathCaptor.getValue().endsWith("/TEST_APP.json"));
  }

  @Test
  void shouldReadLatestRuntimeLockFromVcsLatestSlotPath() {
    when(vcsManagerCenter.getVcsManager("crm")).thenReturn(vcsManager);
    when(vcsManager.getFile(anyString())).thenReturn(vcsFile);
    when(vcsFile.exists()).thenReturn(true);
    when(vcsFile.getContent()).thenReturn("{\n"
        + "  \"slot\" : {\n"
        + "    \"appId\" : \"crm\",\n"
        + "    \"profileId\" : \"alice/dev\",\n"
        + "    \"runtimeKind\" : \"TEST_APP\"\n"
        + "  },\n"
        + "  \"savedAt\" : \"2026-03-24T12:00:00Z\",\n"
        + "  \"lock\" : {\n"
        + "    \"appId\" : \"crm\",\n"
        + "    \"profileId\" : \"alice/dev\",\n"
        + "    \"runtimeKind\" : \"TEST_APP\",\n"
        + "    \"status\" : \"READY\",\n"
        + "    \"resolvedAt\" : \"2026-03-24T12:00:00Z\",\n"
        + "    \"baseBomVersion\" : \"2026.03\",\n"
        + "    \"topLevelArtifacts\" : [ {\n"
        + "      \"appId\" : \"crm\",\n"
        + "      \"groupId\" : \"com.example\",\n"
        + "      \"artifactId\" : \"crm-runtime\",\n"
        + "      \"version\" : \"1.0.0\",\n"
        + "      \"packaging\" : \"jar\",\n"
        + "      \"enabled\" : true,\n"
        + "      \"usage\" : \"runtime\"\n"
        + "    } ],\n"
        + "    \"resolvedArtifacts\" : [ ],\n"
        + "    \"suppressedArtifacts\" : [ ],\n"
        + "    \"diagnostics\" : [ \"base BOM first: 2026.03\" ]\n"
        + "  }\n"
        + "}");

    Optional<RuntimeLockRecord> found = storage.findLatestSuccessful(RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP));

    assertTrue(found.isPresent());
    assertEquals("1.0.0", found.get().getLock().getTopLevelArtifacts().get(0).getVersion());
    assertEquals(RuntimeLockStatus.READY, found.get().getLock().getStatus());
  }

  @Test
  void shouldPreserveSavedAtWhenReadingLatestRuntimeLock() {
    when(vcsManagerCenter.getVcsManager("crm")).thenReturn(vcsManager);
    when(vcsManager.getFile(anyString())).thenReturn(vcsFile);
    when(vcsFile.exists()).thenReturn(true);
    when(vcsFile.getContent()).thenReturn("{\n"
        + "  \"slot\" : {\n"
        + "    \"appId\" : \"crm\",\n"
        + "    \"profileId\" : \"alice/dev\",\n"
        + "    \"runtimeKind\" : \"TEST_APP\"\n"
        + "  },\n"
        + "  \"savedAt\" : \"2026-03-24T13:00:00Z\",\n"
        + "  \"lock\" : {\n"
        + "    \"appId\" : \"crm\",\n"
        + "    \"profileId\" : \"alice/dev\",\n"
        + "    \"runtimeKind\" : \"TEST_APP\",\n"
        + "    \"status\" : \"READY\",\n"
        + "    \"resolvedAt\" : \"2026-03-24T12:00:00Z\",\n"
        + "    \"baseBomVersion\" : \"2026.03\",\n"
        + "    \"topLevelArtifacts\" : [ ],\n"
        + "    \"resolvedArtifacts\" : [ ],\n"
        + "    \"suppressedArtifacts\" : [ ],\n"
        + "    \"diagnostics\" : [ ]\n"
        + "  }\n"
        + "}");

    Optional<RuntimeLockRecord> found = storage.findLatestSuccessful(RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP));

    assertTrue(found.isPresent());
    assertEquals(Instant.parse("2026-03-24T13:00:00Z"), found.get().getSavedAt());
  }

  @Test
  void shouldReturnEmptyWhenLatestRuntimeLockFileDoesNotExist() {
    when(vcsManagerCenter.getVcsManager("crm")).thenReturn(vcsManager);
    when(vcsManager.getFile(anyString())).thenReturn(vcsFile);
    when(vcsFile.exists()).thenReturn(false);

    Optional<RuntimeLockRecord> found = storage.findLatestSuccessful(RuntimeLockKey.of("crm", "alice/dev", RuntimeKind.TEST_APP));

    assertTrue(!found.isPresent());
  }

  private void stubCreateOnlyJsonWrites() {
    when(vcsManagerCenter.getVcsManager("crm")).thenReturn(vcsManager);
    when(vcsManager.createFile(anyString())).thenReturn(createFileAction);
    when(createFileAction.content(anyString())).thenReturn(createFileAction);
    when(createFileAction.unlockWhenCommit()).thenReturn(createFileAction);
    when(createFileAction.commit(anyString())).thenReturn(Try.success(Boolean.TRUE));
  }

  private void stubCreateOrUpdateJsonWrites() {
    stubCreateOnlyJsonWrites();
    when(vcsManager.getFile(anyString())).thenReturn(vcsFile);
    when(vcsFile.exists()).thenReturn(false);
  }

  private ResolvedRuntimeLock lock(String version) {
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
        .profileId("alice/dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .status(RuntimeLockStatus.READY)
        .resolvedAt(java.time.Instant.parse("2026-03-24T12:00:00Z"))
        .baseBomVersion("2026.03")
        .topLevelArtifacts(Collections.singletonList(artifact))
        .resolvedArtifacts(Collections.emptyList())
        .suppressedArtifacts(Collections.emptyList())
        .diagnostics(Collections.singletonList("base BOM first: 2026.03"))
        .build();
  }
}
