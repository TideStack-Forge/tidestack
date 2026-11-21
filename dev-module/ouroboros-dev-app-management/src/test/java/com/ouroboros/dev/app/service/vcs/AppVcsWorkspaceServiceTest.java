package com.ouroboros.dev.app.service.vcs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppVcsFileRequest;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.action.VCSAction.BatchAction;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

class AppVcsWorkspaceServiceTest {
  private final VCSManager manager = mock(VCSManager.class);
  private final AppEditAccessEvaluator access = mock(AppEditAccessEvaluator.class);
  private final VCSManagerCenter center = mock(VCSManagerCenter.class);
  private final BatchAction batch = mock(BatchAction.class, RETURNS_SELF);
  private final AppVcsWorkspaceService service = new AppVcsWorkspaceService(center, access);

  @BeforeEach
  void setup() {
    when(center.getVcsManager("crm")).thenReturn(manager);
    when(access.canEdit(eq("crm"), nullable(String.class))).thenReturn(true);
    when(manager.batchOperation()).thenReturn(batch);
    when(batch.commit(anyString())).thenReturn(Try.success(true));
    when(manager.unlock(anyList(), anyBoolean())).thenReturn(Try.success(null));
    when(manager.walkDirectory("./", null)).thenAnswer(ignored -> Stream.empty());
    when(manager.getUncommittedFiles(".")).thenReturn(List.of());
  }

  @Test
  void listsDeletedAndExistingFilesWithOwnerSpecificCapabilities() {
    var mine = file("models/order.json", true, LockState.OWNED);
    var deleted = file("models/removed.json", false, LockState.LOCKED);
    when(manager.walkDirectory("./", null)).thenAnswer(ignored -> Stream.of(mine));
    when(manager.getUncommittedFiles(".")).thenReturn(List.of(mine, deleted));
    when(access.canManageApp(eq("crm"), nullable(String.class))).thenReturn(true);
    var files = service.getFiles("crm", true).get();
    assertEquals(2, files.size());
    assertTrue(files.getFirst().canCommit());
    assertTrue(files.getFirst().canUnlock());
    assertFalse(files.getFirst().canForceUnlock());
    assertFalse(files.getLast().exists());
    assertFalse(files.getLast().canCommit());
    assertTrue(files.getLast().canForceUnlock());
  }

  @Test
  void commitsSelectedOwnedFilesTogetherThroughExistingBatchAndPreservesLocks() {
    var mine = file("mine.json", true, LockState.OWNED);
    var removed = file("removed.json", false, LockState.OWNED);
    var other = file("other.json", true, LockState.LOCKED);
    when(manager.getUncommittedFiles(".")).thenReturn(List.of(mine, removed, other));
    assertTrue(service.commit("crm", request(List.of("mine.json", "removed.json"), false)).get());
    verify(batch).add("mine.json");
    verify(batch).remove("removed.json");
    verify(batch, never()).add("other.json");
    verify(batch, never()).unlockWhenCommit();
    verify(batch).commit("修改订单");
  }

  @Test
  void validatesWholeSelectionBeforeStartingAnyCommit() {
    var files = List.of(file("mine.json", true, LockState.OWNED), file("other.json", true, LockState.LOCKED));
    when(manager.getUncommittedFiles(".")).thenReturn(files);
    assertTrue(service.commit("crm", request(List.of("mine.json", "other.json"), false)).isFailure());
    verify(manager, never()).batchOperation();
  }

  @Test
  void rechecksOwnershipBeforeBatchWrite() {
    var mine = file("mine.json", true, LockState.OWNED);
    when(manager.getUncommittedFiles(".")).thenReturn(List.of(mine));
    doAnswer(call -> {
      when(mine.getLockState()).thenReturn(LockState.LOCKED);
      Runnable validation = call.getArgument(0);
      when(batch.commit(anyString())).thenAnswer(ignored -> Try.of(() -> { validation.run(); return true; }));
      return batch;
    }).when(batch).beforeWrite(any());
    assertTrue(service.commit("crm", request(List.of("mine.json"), false)).isFailure());
  }

  @Test
  void rejectsMissingEmptyUnchangedAndUnauthorizedCommitSelections() {
    assertTrue(service.commit("crm", request(List.of("../outside"), false)).isFailure());
    assertTrue(service.commit("crm", request(List.of(), false)).isFailure());
    assertTrue(service.commit("crm", new AppVcsFileRequest(List.of("a"), " ", false)).isFailure());
    var unchanged = file("same.json", true, LockState.OWNED);
    when(unchanged.getVersionState()).thenReturn(VersionState.NOTCHANGED);
    when(manager.getUncommittedFiles(".")).thenReturn(List.of(unchanged));
    assertTrue(service.commit("crm", request(List.of("same.json"), false)).isFailure());
    when(access.canEdit(eq("crm"), nullable(String.class))).thenReturn(false);
    assertTrue(service.commit("crm", request(List.of("same.json"), false)).isFailure());
    verify(manager, never()).batchOperation();
  }

  @Test
  void onlyAdministratorCanForceUnlockAnotherEditorsFile() {
    var other = file("other.json", true, LockState.LOCKED);
    when(manager.getUncommittedFiles(".")).thenReturn(List.of(other));
    assertTrue(service.unlock("crm", request(List.of("other.json"), true)).isFailure());
    assertTrue(service.unlock("crm", request(List.of("other.json"), false)).isFailure());
    verify(manager, never()).unlock(anyList(), anyBoolean());
    when(access.canManageApp(eq("crm"), nullable(String.class))).thenReturn(true);
    assertTrue(service.unlock("crm", request(List.of("other.json"), true)).isSuccess());
    verify(manager).unlock(List.of("other.json"), true);
  }

  @Test
  void allowsOwnedUnlockAndReturnsUnderlyingFailures() {
    var mine = file("mine.json", true, LockState.OWNED);
    when(manager.getUncommittedFiles(".")).thenReturn(List.of(mine));
    assertTrue(service.unlock("crm", request(List.of("mine.json"), false)).isSuccess());
    when(batch.commit(anyString())).thenReturn(Try.failure(new IllegalStateException("write failed")));
    assertEquals("write failed", service.commit("crm", request(List.of("mine.json"), false)).getCause().getMessage());
  }

  @Test
  void readsOnlyCurrentWorkspaceFiles() {
    var file = file("mine.json", true, LockState.OWNED);
    when(file.getContent()).thenReturn("{\"name\":\"Order\"}");
    when(manager.getUncommittedFiles(".")).thenReturn(List.of(file));
    assertEquals("{\"name\":\"Order\"}", service.getFileContent("crm", "mine.json").get().get("content"));
    assertTrue(service.getFileContent("crm", "../secret").isFailure());
  }

  private AppVcsFileRequest request(List<String> paths, boolean force) {
    return new AppVcsFileRequest(paths, "修改订单", force);
  }

  private VCSFile file(String path, boolean exists, LockState lock) {
    VCSFile file = mock(VCSFile.class);
    when(file.getPath()).thenReturn(path);
    when(file.exists()).thenReturn(exists);
    when(file.getLockState()).thenReturn(lock);
    when(file.getVersionState()).thenReturn(VersionState.CHANGED);
    return file;
  }
}
