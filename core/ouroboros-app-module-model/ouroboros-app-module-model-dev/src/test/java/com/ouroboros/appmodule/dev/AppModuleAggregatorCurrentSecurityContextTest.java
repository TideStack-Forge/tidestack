package com.ouroboros.appmodule.dev;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.action.VCSAction.CreateFileAction;
import com.ouroboros.vcs.action.VCSAction.UpdateFileAction;
import com.ouroboros.vcs.file.FileDiff;
import com.ouroboros.vcs.file.FileRevision;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.Tag;
import com.ouroboros.vcs.versioning.VersionState;

import io.vavr.control.Try;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppModuleAggregatorCurrentSecurityContextTest {

  private static final String DIFF_FILE_NAME = "modules.diff.json";

  private TestableAppModuleAggregator aggregator;
  private TestVcsManager testVcsManager;

  @BeforeEach
  void setUp() {
    DevAppHolder.setAppName("sales-app");
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();

    testVcsManager = new TestVcsManager();
    aggregator = new TestableAppModuleAggregator(testVcsManager);
  }

  @AfterEach
  void tearDown() {
    DevAppHolder.removeAppName();
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void saveDiffsUsesCurrentSecurityContextWhenAvailable() {
    CurrentSecurityContextHolder.setDelegate(
        () -> Optional.of(new DefaultSecurityIdentity(() -> "alice")));
    testVcsManager.getDiffFile().setExists(true);

    aggregator.saveDiffs(Collections.emptyList());

    assertEquals("alice", testVcsManager.getUpdateAction().getCommitter());
    assertEquals(0, testVcsManager.getCreateAction().getInvocationCount());
  }

  @Test
  void saveDiffsFallsBackToNullCommitterWhenSeamCleared() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("legacy-principal", "N/A", Collections.emptyList()));
    testVcsManager.getDiffFile().setExists(false);

    aggregator.saveDiffs(Collections.emptyList());

    assertNull(testVcsManager.getCreateAction().getCommitter());
    assertTrue(testVcsManager.getCreateAction().getInvocationCount() > 0);
    assertEquals(0, testVcsManager.getUpdateAction().getInvocationCount());
  }

  private static final class TestableAppModuleAggregator extends AppModuleAggregator {
    private final VCSManager manager;

    TestableAppModuleAggregator(VCSManager manager) {
      this.manager = manager;
    }

    @Override
    VCSManager getVcsManager() {
      return manager;
    }
  }

  private static final class TestVcsManager implements VCSManager {
    private final TestVcsFile diffFile = new TestVcsFile();
    private final RecordingUpdateAction updateAction = new RecordingUpdateAction();
    private final RecordingCreateAction createAction = new RecordingCreateAction();

    TestVcsFile getDiffFile() {
      return diffFile;
    }

    RecordingUpdateAction getUpdateAction() {
      return updateAction;
    }

    RecordingCreateAction getCreateAction() {
      return createAction;
    }

    @Override
    public void destroy() {
    }

    @Override
    public VCSFile getFile(String relativePath) {
      return diffFile;
    }

    @Override
    public UpdateFileAction updateFile(String relativePath) {
      updateAction.reset();
      return updateAction;
    }

    @Override
    public CreateFileAction createFile(String relativePath) {
      createAction.reset();
      return createAction;
    }

    @Override
    public Try<Void> lock(String relativePath, boolean force) {
      throw unsupported();
    }

    @Override
    public Try<Void> lock(Collection<String> relativePaths, boolean force) {
      throw unsupported();
    }

    @Override
    public Try<Void> unlock(String relativePath, boolean force) {
      throw unsupported();
    }

    @Override
    public Try<Void> unlock(Collection<String> relativePaths, boolean force) {
      throw unsupported();
    }

    @Override
    public com.ouroboros.vcs.action.VCSAction.RemoveFileAction removeFile(String relativePath) {
      throw unsupported();
    }

    @Override
    public com.ouroboros.vcs.action.VCSAction.MoveFileAction moveFile(String relativePath, String newRelativePath) {
      throw unsupported();
    }

    @Override
    public com.ouroboros.vcs.action.VCSAction.BatchAction batchOperation() {
      throw unsupported();
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String fileExtension) {
      throw unsupported();
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath) {
      throw unsupported();
    }

    @Override
    public List<ChangeLog> getChangeLogs(String relativePath, int skip, int limit) {
      throw unsupported();
    }

    @Override
    public List<ChangeLog> getChangeLogs(Collection<String> relativePaths) {
      throw unsupported();
    }

    @Override
    public List<ChangeLog> getChangeLogs(Collection<String> relativePaths, int skip, int limit) {
      throw unsupported();
    }

    @Override
    public List<VCSFile> getUncommittedFiles(String relativePath) {
      throw unsupported();
    }

    @Override
    public VCSFile getFile(String relativePath, String revision) {
      throw unsupported();
    }

    @Override
    public VCSFile getFileFromBranch(String relativePath, String branchName) {
      throw unsupported();
    }

    @Override
    public VCSFile getFileFromTag(String relativePath, String tagName) {
      throw unsupported();
    }

    @Override
    public List<VCSFile> getFileList(String relativeParentPath, String revision) {
      throw unsupported();
    }

    @Override
    public List<VCSFile> getFileListFromBranch(String relativeParentPath, String branchName) {
      throw unsupported();
    }

    @Override
    public List<VCSFile> getFileListFromTag(String relativeParentPath, String tagName) {
      throw unsupported();
    }

    @Override
    public Stream<VCSFile> walkDirectory(String relativeParentPath, String revision, String fileExtension) {
      throw unsupported();
    }

    @Override
    public Try<String> createTag(String tagName, String message) {
      throw unsupported();
    }

    @Override
    public Try<String> createTag(String tagName, String revision, String message) {
      throw unsupported();
    }

    @Override
    public Try<Boolean> deleteTag(String tagName) {
      throw unsupported();
    }

    @Override
    public Try<List<Tag>> getTagList() {
      throw unsupported();
    }

    @Override
    public Try<String> getCurrentBranch() {
      throw unsupported();
    }

    @Override
    public Try<List<String>> getBranchList() {
      throw unsupported();
    }

    @Override
    public Try<String> createBranch(String branchName) {
      throw unsupported();
    }

    @Override
    public Try<String> createBranch(String branchName, String fromRevision) {
      throw unsupported();
    }

    @Override
    public Try<Boolean> switchBranch(String branchName) {
      throw unsupported();
    }

    @Override
    public Try<com.ouroboros.vcs.VCSConfig.RemoteConfig> getRemoteConfig() {
      throw unsupported();
    }

    @Override
    public Try<Boolean> setRemoteConfig(com.ouroboros.vcs.VCSConfig.RemoteConfig remoteConfig) {
      throw unsupported();
    }

    @Override
    public Try<Boolean> pull() {
      throw unsupported();
    }

    @Override
    public Try<Boolean> push() {
      throw unsupported();
    }

    @Override
    public Try<Boolean> isWorkspaceClean() {
      throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
      return new UnsupportedOperationException("Test stub does not support this operation");
    }
  }

  private static final class TestVcsFile implements VCSFile {
    private boolean exists;

    void setExists(boolean exists) {
      this.exists = exists;
    }

    @Override
    public boolean exists() {
      return exists;
    }

    @Override
    public String getPath() {
      throw unsupported();
    }

    @Override
    public String getFileName() {
      throw unsupported();
    }

    @Override
    public String getContent() {
      throw unsupported();
    }

    @Override
    public InputStream getInputStream() {
      throw unsupported();
    }

    @Override
    public byte[] getBytesArray() {
      throw unsupported();
    }

    @Override
    public Long getSize() {
      throw unsupported();
    }

    @Override
    public LocalDateTime getCreationTime() {
      throw unsupported();
    }

    @Override
    public LocalDateTime getLastCommittedTime() {
      throw unsupported();
    }

    @Override
    public LocalDateTime getLastModifiedTime() {
      throw unsupported();
    }

    @Override
    public VersionState getVersionState() {
      throw unsupported();
    }

    @Override
    public LockState getLockState() {
      throw unsupported();
    }

    @Override
    public String getLockOwner() {
      throw unsupported();
    }

    @Override
    public String getLockOwnerName() {
      throw unsupported();
    }

    @Override
    public Try<Void> lock() {
      throw unsupported();
    }

    @Override
    public Try<Void> lock(boolean forceLock) {
      throw unsupported();
    }

    @Override
    public Try<Void> unlock() {
      throw unsupported();
    }

    @Override
    public Try<Void> unlock(boolean forceUnlock) {
      throw unsupported();
    }

    @Override
    public Try<Boolean> write(String content) {
      throw unsupported();
    }

    @Override
    public Try<Boolean> write(byte[] content) {
      throw unsupported();
    }

    @Override
    public Try<Boolean> write(InputStream content) {
      throw unsupported();
    }

    @Override
    public Try<VCSFile> move(String newPath) {
      throw unsupported();
    }

    @Override
    public Try<VCSFile> remove() {
      throw unsupported();
    }

    @Override
    public Try<Boolean> commit(String message) {
      throw unsupported();
    }

    @Override
    public Try<Boolean> rollback(boolean forceRollback) {
      throw unsupported();
    }

    @Override
    public String getRevision() {
      throw unsupported();
    }

    @Override
    public boolean isHistorical() {
      throw unsupported();
    }

    @Override
    public VCSFile atRevision(String revision) {
      throw unsupported();
    }

    @Override
    public VCSFile atBranch(String branchName) {
      throw unsupported();
    }

    @Override
    public VCSFile atTag(String tagName) {
      throw unsupported();
    }

    @Override
    public Try<FileDiff> diffWith(String otherRevision) {
      throw unsupported();
    }

    @Override
    public Try<FileDiff> diffWithWorkingCopy() {
      throw unsupported();
    }

    @Override
    public List<FileRevision> getRevisions() {
      throw unsupported();
    }

    @Override
    public List<FileRevision> getRevisions(int skip, int limit) {
      throw unsupported();
    }

    private static UnsupportedOperationException unsupported() {
      return new UnsupportedOperationException("Test stub does not support this operation");
    }
  }

  private abstract static class RecordingVcsAction<T extends com.ouroboros.vcs.action.VCSAction<T>>
      implements com.ouroboros.vcs.action.VCSAction<T> {

    @SuppressWarnings("unchecked")
    protected T self() {
      return (T) this;
    }

    @Override
    public T committer(String user) {
      return self();
    }

    @Override
    public T committerEmail(String email) {
      return self();
    }

    @Override
    public T autoLock() {
      return self();
    }

    @Override
    public T unlockWhenCommit() {
      return self();
    }

    @Override
    public Try<Boolean> write(boolean forceWrite) {
      throw unsupported();
    }

    @Override
    public Try<Boolean> commit(String message) {
      return Try.success(true);
    }

    protected static UnsupportedOperationException unsupported() {
      return new UnsupportedOperationException("Test stub does not support this operation");
    }
  }

  private static final class RecordingUpdateAction extends RecordingVcsAction<UpdateFileAction>
      implements UpdateFileAction {
    private String committer;
    private int invocationCount;

    @Override
    public UpdateFileAction content(String content) {
      invocationCount++;
      return this;
    }

    @Override
    public UpdateFileAction committer(String user) {
      this.committer = user;
      return super.committer(user);
    }

    @Override
    public Try<Boolean> commit(String message) {
      return super.commit(message);
    }

    @Override
    public UpdateFileAction content(byte[] content) {
      throw RecordingVcsAction.unsupported();
    }

    @Override
    public UpdateFileAction content(InputStream content) {
      throw RecordingVcsAction.unsupported();
    }

    String getCommitter() {
      return committer;
    }

    int getInvocationCount() {
      return invocationCount;
    }

    void reset() {
      invocationCount = 0;
      committer = null;
    }
  }

  private static final class RecordingCreateAction extends RecordingVcsAction<CreateFileAction>
      implements CreateFileAction {
    private String committer;
    private int invocationCount;

    @Override
    public CreateFileAction content(String content) {
      invocationCount++;
      return this;
    }

    @Override
    public CreateFileAction committer(String user) {
      this.committer = user;
      return super.committer(user);
    }

    @Override
    public CreateFileAction content(byte[] content) {
      throw RecordingVcsAction.unsupported();
    }

    @Override
    public CreateFileAction content(InputStream content) {
      throw RecordingVcsAction.unsupported();
    }

    String getCommitter() {
      return committer;
    }

    int getInvocationCount() {
      return invocationCount;
    }

    void reset() {
      invocationCount = 0;
      committer = null;
    }
  }
}
