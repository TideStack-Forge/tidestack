package com.ouroboros.vcs.action;

import org.apache.commons.lang3.StringUtils;

import io.vavr.control.Try;

import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.versioning.VersioningManager;
import com.ouroboros.vcs.exception.VCSPostCommitException;

public abstract class AbstractVCSAction<T extends VCSAction<T>> implements VCSAction<T> {
  private static final String EMPTY_COMMIT_MESSAGE = "<空>";
  protected final VersioningManager versioningManager;
  protected final VCSUserProvider vcsUserProvider;
  protected String commitUser;
  protected String commitUserEmail;
  protected boolean written = false;
  protected boolean committed = false;
  protected boolean autoLock = false;
  protected boolean unlockWhenCommit = false;
  private Throwable writeFailure;

  public AbstractVCSAction(VersioningManager versioningManager, VCSUserProvider vcsUserProvider) {
    this.versioningManager = versioningManager;
    this.vcsUserProvider = vcsUserProvider;
  }

  @Override
  public T committer(String user) {
    this.commitUser = user;
    return (T) this;
  }

  @Override
  public T committerEmail(String email) {
    this.commitUserEmail = email;
    return (T) this;
  }

  @Override
  public T autoLock() {
    this.autoLock = true;
    return (T) this;
  }

  @Override
  public T unlockWhenCommit() {
    this.unlockWhenCommit = true;
    return (T) this;
  }

  @Override
  public Try<Boolean> write(boolean forceWrite) {
    // 检查是否已写
    if (written) {
      return writeFailure == null ? Try.success(false) : Try.failure(writeFailure);
    }
    written = true;

    // 如果非强制写入的话，检查是否被锁定
    var finalUser = commitUser != null
        ? commitUser
        : vcsUserProvider.getCurrentUser();
    if (!forceWrite) {
      var checkLockResult = doCheckLock(finalUser);
      if (checkLockResult.isFailure()) {
        writeFailure = checkLockResult.getCause();
        return Try.failure(checkLockResult.getCause());
      }
    }

    // 执行写入动作
    return Try.of(this::doWriteContent).onFailure(failure -> writeFailure = failure);
  }

  @Override
  public Try<Boolean> commit(String commitMessage) {
    if (writeFailure != null) {
      return Try.failure(writeFailure);
    }
    String user = commitUser == null ? vcsUserProvider.getCurrentUser() : commitUser;
    String message = commitMessage == null ? EMPTY_COMMIT_MESSAGE : commitMessage;

    // 必须检查是否被锁定
    var checkLockResult = autoLock ? doLock(user) : doCheckLock(user);
    if (checkLockResult.isFailure()) {
      return Try.failure(checkLockResult.getCause());
    }

    // 如果第一次写入，则检查是否写入成功，如果写入失败则没有可提交的内容，直接返回失败
    if (!written) {
      var writeResult = write(false);
      if (writeResult.isFailure()) {
        return Try.failure(writeResult.getCause());
      }
      // WARN: 先不检查是否写入成功，因为 VersionControlManager 的实现中，如果文件未改变则不会提交
    }

    // 如果已经提交过，则直接返回失败
    if (committed) {
      return Try.success(false);
    }

    // 准备提交操作链式对象
    var chain = versioningManager.startChain()
        .user(user);
    if (commitUserEmail != null) {
      chain.email(commitUserEmail);
    }

    // 向链式对象中添加提交内容
    // WARN: 暂时不检查是否成功，因为 VersionControlManager 的实现中会自动避免不必要的提交
    doPrepareCommitContent(chain);

    // 执行提交动作
    var result = chain.commit(message).onSuccess(ignore -> committed = true);

    if (unlockWhenCommit) {
      result = result.andThen(ignore -> Try.run(() -> doUnlock(user))
          .getOrElseThrow(VCSPostCommitException::new));
    }
    return result
        .map(StringUtils::isNotEmpty);
  }

  abstract protected Try<Void> doCheckLock(String user);

  abstract protected Try<Void> doLock(String user);

  abstract protected void doUnlock(String user);

  abstract protected boolean doWriteContent();

  abstract protected void doPrepareCommitContent(VersioningManager.OperationChain chain);
}
