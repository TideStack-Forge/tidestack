package com.ouroboros.vcs.action;


import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import io.vavr.control.Try;

import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.exception.FileLockedException;
import com.ouroboros.vcs.filesystem.FileSystemManager;
import com.ouroboros.vcs.lock.LockManager;
import com.ouroboros.vcs.versioning.VersioningManager;

public class DefaultMoveFileAction extends AbstractVCSAction<VCSAction.MoveFileAction> implements VCSAction.MoveFileAction {
  private final LockManager lockManager;
  private final FileSystemManager fileSystemManager;

  private String fromPath;
  private String toPath;

  private Function<String, String> contentUpdater;

  public DefaultMoveFileAction(VersioningManager versioningManager, FileSystemManager fileSystemManager, LockManager lockManager, VCSUserProvider vcsUserProvider) {
    super(versioningManager, vcsUserProvider);
    this.lockManager = lockManager;
    this.fileSystemManager = fileSystemManager;
  }

  @Override
  protected boolean doWriteContent() {
    if (StringUtils.isEmpty(fromPath) || StringUtils.isEmpty(toPath)) {
      return false;
    }
    if (!fileSystemManager.exists(fromPath)) {
      return false;
    }
    if (fileSystemManager.exists(toPath)) {
      return false;
    }

    var moveResult = fileSystemManager.move(fromPath, toPath)
        .peek(ignore -> {
          versioningManager.notifyFileChange(fromPath);
          versioningManager.notifyFileChange(toPath);
        })
        .isSuccess();
    if (!moveResult) {
      return false;
    }
    if (contentUpdater == null) {
      return true;
    }

    var content = fileSystemManager.getFileContent(toPath);
    var newContent = content.mapTry(old -> contentUpdater.apply(old));
    var writeResult = newContent.flatMap(n -> fileSystemManager.write(toPath, n));

    return writeResult.isSuccess();
  }

  @Override
  public MoveFileAction from(String relativePath) {
    fromPath = relativePath;
    return this;
  }

  @Override
  public MoveFileAction to(String newRelativePath) {
    toPath = newRelativePath;
    return this;
  }

  @Override
  public MoveFileAction updateContent(Function<String, String> contentUpdater) {
    this.contentUpdater = contentUpdater;
    return this;
  }

  @Override
  protected void doPrepareCommitContent(VersioningManager.OperationChain chain) {
    chain.remove(fromPath).add(toPath);
  }

  @Override
  protected Try<Void> doCheckLock(String user) {
    var lockOwner = lockManager.getLockOwner(fromPath);
    if (StringUtils.isEmpty(lockOwner)) {
      return Try.success(null);
    }
    if (lockOwner.equals(user)) {
      return Try.success(null);
    }
    return Try.failure(new FileLockedException(fromPath, lockOwner));
  }

  @Override
  protected Try<Void> doLock(String user) {
    return lockManager.lock(fromPath, user);
  }

  @Override
  protected void doUnlock(String user) {
    lockManager.unlock(fromPath, user);
  }
}