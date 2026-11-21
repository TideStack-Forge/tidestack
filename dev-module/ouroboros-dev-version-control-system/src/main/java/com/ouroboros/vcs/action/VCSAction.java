package com.ouroboros.vcs.action;

import java.io.InputStream;
import java.util.Collection;
import java.util.function.Function;

import io.vavr.control.Try;

public interface VCSAction<T extends VCSAction<T>> {
  T committer(String user);

  T committerEmail(String email);

  T autoLock();

  T unlockWhenCommit();

  default Try<Boolean> write() {
    return write(false);
  }

  Try<Boolean> write(boolean forceWrite);

  Try<Boolean> commit(String message);

  // 创建文件操作
  interface CreateFileAction extends VCSAction<CreateFileAction> {
    CreateFileAction content(String content);

    CreateFileAction content(byte[] content);

    CreateFileAction content(InputStream content);
  }

  // 删除文件操作
  interface RemoveFileAction extends VCSAction<RemoveFileAction> {
  }

  // 更新文件操作
  interface UpdateFileAction extends VCSAction<UpdateFileAction> {
    UpdateFileAction content(String content);

    UpdateFileAction content(byte[] content);

    UpdateFileAction content(InputStream content);
  }

  // 移动文件操作
  interface MoveFileAction extends VCSAction<MoveFileAction> {
    MoveFileAction from(String relativePath);

    MoveFileAction to(String newRelativePath);

    MoveFileAction updateContent(Function<String, String> updateContent);
  }

  // 批量混合操作
  interface BatchAction extends VCSAction<BatchAction> {
    /** Run after write locks are checked and before any resource is changed. */
    default BatchAction beforeWrite(Runnable validation) {
      throw new UnsupportedOperationException("当前 VCS 批次不支持写前校验");
    }

    BatchAction add(String relativePath);

    BatchAction add(Collection<String> relativePaths);

    BatchAction create(String relativePath, String content);

    BatchAction create(String relativePath, byte[] content);

    BatchAction create(String relativePath, InputStream content);

    BatchAction remove(String relativePath);

    BatchAction remove(Collection<String> relativePaths);

    BatchAction update(String relativePath, String content);

    BatchAction update(String relativePath, byte[] content);

    BatchAction update(String relativePath, InputStream content);

    BatchAction move(String relativePath, String newRelativePath);

    BatchAction move(String relativePath, String newRelativePath, Function<String, String> updateContent);
  }
}
