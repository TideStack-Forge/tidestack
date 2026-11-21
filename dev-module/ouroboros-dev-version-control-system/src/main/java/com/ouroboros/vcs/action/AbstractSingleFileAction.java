package com.ouroboros.vcs.action;


import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import io.vavr.control.Try;

import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.exception.FileLockedException;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.lock.LockManager;
import com.ouroboros.vcs.versioning.VersioningManager;

public abstract class AbstractSingleFileAction<T extends VCSAction<T>> extends AbstractVCSAction<T> {
  protected final VCSFile file;
  private final LockManager lockManager;

  protected Supplier<Boolean> contentWriter = () -> true;

  public AbstractSingleFileAction(VersioningManager versioningManager, LockManager lockManager, VCSUserProvider vcsUserProvider, VCSFile file) {
    super(versioningManager, vcsUserProvider);
    this.lockManager = lockManager;
    this.file = file;
  }

  @Override
  protected boolean doWriteContent() {
    var writeResult = contentWriter.get();
    if (writeResult) {
      versioningManager.notifyFileChange(file.getPath());
    }
    return writeResult;
  }

  protected Try<Void> doLock() {
    if (autoLock) {
      return file.lock(false).map(ignore -> null);
    }
    return Try.success(null);
  }

  @Override
  protected Try<Void> doCheckLock(String user) {
    var lockOwner = file.getLockOwner();
    if (StringUtils.isEmpty(lockOwner)) {
      return Try.success(null);
    }
    return lockOwner.equals(user)
        ? Try.success(null)
        : Try.failure(new FileLockedException(file.getPath(), lockOwner));
  }

  @Override
  protected Try<Void> doLock(String user) {
    return lockManager.lock(file.getPath(), user);
  }

  @Override
  protected void doUnlock(String user) {
    lockManager.unlock(file.getPath(), user);
  }
}