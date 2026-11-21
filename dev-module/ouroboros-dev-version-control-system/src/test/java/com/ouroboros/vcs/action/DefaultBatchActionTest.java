package com.ouroboros.vcs.action;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.nio.file.attribute.PosixFilePermission;

import org.eclipse.jgit.api.Git;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Try;

import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.filesystem.LocalFileSystemManager;
import com.ouroboros.vcs.lock.LocalFileLockManager;
import com.ouroboros.vcs.versioning.git.GitManager;
import com.ouroboros.vcs.exception.VCSPostCommitException;

class DefaultBatchActionTest {
  private static final byte[] ORIGINAL = "{\r\n  \"script\": \"保留原文\\n\"\r\n}\r\n".getBytes(StandardCharsets.UTF_8);
  @TempDir Path home;
  private FailingFileSystem files;
  private LocalFileLockManager locks;
  private GitManager versions;
  private final VCSUserProvider user = new VCSUserProvider() {
    public String getCurrentUser() { return "modeler"; }
    public String getCurrentUserEmail() { return "modeler@example.test"; }
  };

  @BeforeEach
  void prepareRepository() throws Exception {
    Path repository = Files.createDirectory(home.resolve("metadata"));
    files = new FailingFileSystem(repository.toString());
    locks = new LocalFileLockManager(home.resolve("metadata.lock").toString());
    versions = new GitManager(repository.toString(), "main");
    files.createFile("model.json", ORIGINAL).get();
    files.createFile("occupied.json", "existing").get();
    assertFalse(versions.startChain().user(user.getCurrentUser()).email(user.getCurrentUserEmail())
        .add("model.json").add("occupied.json").commit("baseline").get().isBlank());
  }

  @AfterEach
  void releaseRepository() {
    locks.destroy(); versions.destroy(); files.destroy();
  }

  private DefaultBatchAction batch() { return new DefaultBatchAction(versions, files, locks, user); }

  @Test
  void writeValidationRunsWithLocksBeforeChangingAnyResourceAndFailureReleasesNewLocks() {
    var failure = new IllegalStateException("preview became stale");
    var operation = batch().update("model.json", "2.0").create("flows/approve.json", "script")
        .beforeWrite(() -> {
          assertEquals("modeler", locks.getLockOwner("model.json"));
          assertEquals("modeler", locks.getLockOwner("flows/approve.json"));
          assertArrayEquals(ORIGINAL, files.getFileBytes("model.json").get());
          assertFalse(files.exists("flows/approve.json"));
          throw failure;
        }).autoLock();
    assertSame(failure, operation.commit("stale upgrade").getCause());
    assertArrayEquals(ORIGINAL, files.getFileBytes("model.json").get());
    assertFalse(files.exists("flows/approve.json"));
    assertNull(locks.getLockOwner("model.json"));
    assertNull(locks.getLockOwner("flows/approve.json"));
    assertSame(failure, operation.commit("retry").getCause());
    assertTrue(versions.isWorkspaceClean().get());
  }

  @Test
  void successfulResourceSetCanBeStagedAndCommittedThroughTheSameAction() {
    var operation = batch();
    assertTrue(operation.update("model.json", "new model").create("flows/approve.json", "new flow").write().get());
    assertEquals("new model", files.getFileContent("model.json").get());
    assertEquals("new flow", files.getFileContent("flows/approve.json").get());
    assertTrue(operation.committerEmail(user.getCurrentUserEmail()).commit("upgrade one model").get());
    assertTrue(versions.isWorkspaceClean().get());
    assertEquals("new model", versions.getFileContent("model.json", "HEAD").get());
  }

  @Test
  void failureRestoresExactUncommittedBytesAndRemovesNewResourcesWithoutCommitting() {
    byte[] previousHead = versions.getFileBytes("model.json", "HEAD").get();
    byte[] unsaved = "uncommitted\r\nscript".getBytes(StandardCharsets.UTF_8);
    files.write("model.json", unsaved).get();
    var result = batch().update("model.json", "2.0").create("flows/approve.json", "script")
        .create("occupied.json", "collision").commit("must not commit");
    assertTrue(result.isFailure());
    assertArrayEquals(unsaved, files.getFileBytes("model.json").get());
    assertFalse(files.exists("flows/approve.json"));
    assertEquals("existing", files.getFileContent("occupied.json").get());
    assertArrayEquals(previousHead, versions.getFileBytes("model.json", "HEAD").get());
    assertEquals(1, versions.getChangeLogs("model.json").size());
  }

  @Test
  void failedActionCannotBeReusedToCommitOrMasqueradeAsASuccessfulWrite() {
    var operation = batch();
    var result = operation.update("model.json", "2.0").create("occupied.json", "collision").write();
    assertTrue(result.isFailure());
    assertSame(result.getCause(), operation.write().getCause());
    assertSame(result.getCause(), operation.commit("must still fail").getCause());
    assertTrue(versions.isWorkspaceClean().get());
    assertEquals(1, versions.getChangeLogs("model.json").size());
  }

  @Test
  void partiallyWrittenFailingFileIsRestoredAndOriginalCauseIsPreserved() {
    files.failWrite = "model.json";
    var result = batch().create("flows/approve.json", "script").update("model.json", "new").write();
    assertTrue(result.isFailure());
    assertSame(files.writeFailure, result.getCause());
    assertArrayEquals(ORIGINAL, files.getFileBytes("model.json").get());
    assertFalse(files.exists("flows/approve.json"));
  }

  @Test
  void unreadableSnapshotRejectsTheWholeSetBeforeAnyWrite() {
    files.failRead = "model.json";
    var result = batch().create("flows/approve.json", "script").update("model.json", "new").write();
    assertTrue(result.isFailure());
    files.failRead = "";
    assertArrayEquals(ORIGINAL, files.getFileBytes("model.json").get());
    assertFalse(files.exists("flows/approve.json"));
  }

  @Test
  void removeAndMoveOperationsRestoreEveryOriginalPathWhenALaterOperationFails() {
    var result = batch().remove(List.of("model.json"))
        .move("occupied.json", "moved.json", text -> text + " changed")
        .create("moved.json", "collision").write();
    assertTrue(result.isFailure());
    assertArrayEquals(ORIGINAL, files.getFileBytes("model.json").get());
    assertEquals("existing", files.getFileContent("occupied.json").get());
    assertFalse(files.exists("moved.json"));
  }

  @Test
  void lockDenialDoesNotMutateAnyFile() {
    locks.lock("model.json", "another-modeler").get();
    var result = batch().create("flows/approve.json", "script").update("model.json", "new").write();
    assertTrue(result.isFailure());
    assertArrayEquals(ORIGINAL, files.getFileBytes("model.json").get());
    assertFalse(files.exists("flows/approve.json"));
  }

  @Test
  void rollbackFailureRemainsVisibleWhileOtherOriginalFilesAreStillRestored() {
    files.failRestore = true;
    var result = batch().update("model.json", "2.0").create("flows/approve.json", "script")
        .create("occupied.json", "collision").write();
    assertTrue(result.isFailure());
    assertTrue(result.getCause().getSuppressed().length > 0);
    assertFalse(files.exists("flows/approve.json"));
    assertEquals("existing", files.getFileContent("occupied.json").get());
  }

  @Test
  void rejectedGitCommitRestoresExactWorkspaceAndIndexWithoutConsumingUnrelatedStagedChanges() throws Exception {
    Path repository = home.resolve("metadata");
    files.write("model.json", "earlier staged content").get();
    files.write("occupied.json", "unrelated staged change").get();
    try (var git = Git.open(repository.toFile())) {
      git.add().addFilepattern("occupied.json").addFilepattern("model.json").call();
      files.write("model.json", "uncommitted original").get();
      var index = git.getRepository().readDirCache();
      var originalEntry = index.getEntry("model.json").getObjectId();
      var unrelatedEntry = index.getEntry("occupied.json").getObjectId();
      Path hook = repository.resolve(".git/hooks/pre-commit");
      Files.writeString(hook, "#!/bin/sh\nexit 1\n");
      Files.setPosixFilePermissions(hook, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE));
      locks.lock("model.json", user.getCurrentUser()).get();
      var operation = batch().committerEmail(user.getCurrentUserEmail()).autoLock().unlockWhenCommit();
      var result = operation.update("model.json", "2.0").create("flows/approve.json", "script").commit("rejected upgrade");
      assertTrue(result.isFailure());
      assertEquals("uncommitted original", files.getFileContent("model.json").get());
      assertFalse(files.exists("flows/approve.json"));
      var restored = git.getRepository().readDirCache();
      assertEquals(originalEntry, restored.getEntry("model.json").getObjectId());
      assertEquals(unrelatedEntry, restored.getEntry("occupied.json").getObjectId());
      assertNull(restored.getEntry("flows/approve.json"));
      assertEquals(Set.of("occupied.json", "model.json"), git.status().call().getChanged());
      assertEquals(Set.of("model.json"), git.status().call().getModified());
      assertSame(result.getCause(), operation.commit("cannot retry restored batch").getCause());
      assertSame(result.getCause(), operation.write().getCause());
      assertEquals(1, versions.getChangeLogs("model.json").size());
      assertEquals(user.getCurrentUser(), locks.getLockOwner("model.json"));
      assertNull(locks.getLockOwner("flows/approve.json"));
    }
  }

  @Test
  void successfulBatchCommitIncludesOnlyItsOwnPathsAndPreservesUnrelatedIndexContents() throws Exception {
    Path repository = home.resolve("metadata");
    files.write("occupied.json", "unrelated staged change").get();
    try (var git = Git.open(repository.toFile())) {
      git.add().addFilepattern("occupied.json").call();
      var unrelatedEntry = git.getRepository().readDirCache().getEntry("occupied.json").getObjectId();
      assertTrue(batch().committerEmail(user.getCurrentUserEmail()).update("model.json", "2.0")
          .create("flows/approve.json", "script").commit("upgrade only").get());
      assertEquals("existing", versions.getFileContent("occupied.json", "HEAD").get());
      assertEquals(unrelatedEntry, git.getRepository().readDirCache().getEntry("occupied.json").getObjectId());
      assertEquals(Set.of("occupied.json"), git.status().call().getChanged());
      assertTrue(git.status().call().getModified().isEmpty());
      assertEquals("2.0", versions.getFileContent("model.json", "HEAD").get());
      assertEquals("script", versions.getFileContent("flows/approve.json", "HEAD").get());
    }
  }

  @Test
  void lockDenialRestoresOnlyLocksAcquiredByThisAttempt() {
    locks.lock("model.json", user.getCurrentUser()).get();
    locks.lock("occupied.json", "another-modeler").get();
    var result = batch().autoLock().create("flows/approve.json", "script").update("model.json", "2.0")
        .update("occupied.json", "must not write").commit("rejected lock");
    assertTrue(result.isFailure());
    assertEquals(user.getCurrentUser(), locks.getLockOwner("model.json"));
    assertEquals("another-modeler", locks.getLockOwner("occupied.json"));
    assertNull(locks.getLockOwner("flows/approve.json"));
    assertArrayEquals(ORIGINAL, files.getFileBytes("model.json").get());
    assertFalse(files.exists("flows/approve.json"));
  }

  @Test
  void postCommitUnlockFailureIsExplicitAndNeverRestoresAlreadyCommittedContent() {
    var cleanupFailure = new IllegalStateException("unlock failed");
    var operation = new DefaultBatchAction(versions, files, locks, user) {
      @Override protected void doUnlock(String username) { throw cleanupFailure; }
    };
    var result = operation.autoLock().unlockWhenCommit().committerEmail(user.getCurrentUserEmail())
        .update("model.json", "2.0").create("flows/approve.json", "script").commit("committed but locked");
    assertTrue(result.isFailure());
    assertInstanceOf(VCSPostCommitException.class, result.getCause());
    assertSame(cleanupFailure, result.getCause().getCause());
    assertEquals("2.0", files.getFileContent("model.json").get());
    assertEquals("2.0", versions.getFileContent("model.json", "HEAD").get());
    assertEquals("script", versions.getFileContent("flows/approve.json", "HEAD").get());
    assertTrue(versions.isWorkspaceClean().get());
  }

  private static class FailingFileSystem extends LocalFileSystemManager {
    String failWrite = "";
    String failRead = "";
    boolean failRestore;
    final RuntimeException writeFailure = new IllegalStateException("injected partial write failure");
    FailingFileSystem(String repository) { super(repository); }
    @Override public Try<Void> write(String path, InputStream content) {
      if (failWrite.equals(path)) {
        failWrite = "";
        super.write(path, new byte[] {1, 2}).get();
        return Try.failure(writeFailure);
      }
      return super.write(path, content);
    }
    @Override public Try<Void> write(String path, byte[] content) {
      if (failRestore && "model.json".equals(path)) {
        return Try.failure(new IllegalStateException("injected restoration failure"));
      }
      return super.write(path, content);
    }
    @Override public Try<byte[]> getFileBytes(String path) {
      return failRead.equals(path) ? Try.failure(new IllegalStateException("injected snapshot read failure")) : super.getFileBytes(path);
    }
  }
}
