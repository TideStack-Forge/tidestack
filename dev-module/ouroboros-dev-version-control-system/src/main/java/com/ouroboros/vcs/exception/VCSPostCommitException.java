package com.ouroboros.vcs.exception;

/** The content commit succeeded; retrying or compensating it would be incorrect. */
public final class VCSPostCommitException extends VCSException {
  public VCSPostCommitException(Throwable cause) {
    super("内容已提交，但释放文件锁失败；请检查并释放当前操作持有的锁", cause);
  }
}
