package com.ouroboros.vcs.file;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import io.vavr.control.Try;

import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.VersionState;

public interface VCSFile {
  boolean exists();

  String getPath();

  String getFileName();

  String getContent();

  InputStream getInputStream();

  byte[] getBytesArray();

  Long getSize();

  LocalDateTime getCreationTime();

  LocalDateTime getLastCommittedTime();

  LocalDateTime getLastModifiedTime();

  VersionState getVersionState();

  LockState getLockState();

  String getLockOwner();

  String getLockOwnerName();

  default Try<Void> lock() {
    return lock(false);
  }

  Try<Void> lock(boolean forceLock);

  default Try<Void> unlock() {
    return unlock(false);
  }

  Try<Void> unlock(boolean forceUnlock);

  Try<Boolean> write(String content);

  Try<Boolean> write(byte[] content);

  Try<Boolean> write(InputStream content);

  Try<VCSFile> move(String newPath);

  Try<VCSFile> remove();

  Try<Boolean> commit(String message);

  Try<Boolean> rollback(boolean forceRollback);

  // === 版本上下文信息 ===
  /**
   * 获取文件所属的版本上下文（HEAD, 分支名, 标签名, commit hash等）
   * @return 版本标识，null表示当前工作区
   */
  String getRevision();

  /**
   * 是否为历史版本文件（只读）
   * @return true表示历史版本，false表示当前工作区文件
   */
  boolean isHistorical();

  // === 历史版本操作 ===
  /**
   * 获取指定版本的文件
   * @param revision 版本标识（commit hash, 分支名, 标签名）
   * @return 历史版本文件对象
   */
  VCSFile atRevision(String revision);

  /**
   * 获取指定分支的文件
   * @param branchName 分支名
   * @return 分支版本文件对象
   */
  VCSFile atBranch(String branchName);

  /**
   * 获取指定标签的文件
   * @param tagName 标签名
   * @return 标签版本文件对象
   */
  VCSFile atTag(String tagName);

  /**
   * 与另一个版本比较差异
   * @param otherRevision 比较的目标版本
   * @return 差异信息
   */
  Try<FileDiff> diffWith(String otherRevision);

  /**
   * 与当前工作区版本比较
   * @return 差异信息
   */
  Try<FileDiff> diffWithWorkingCopy();

  /**
   * 获取文件的历史版本列表
   * @return 包含该文件的所有提交记录
   */
  List<FileRevision> getRevisions();

  /**
   * 获取文件的历史版本列表（分页）
   * @param skip 跳过数量
   * @param limit 限制数量
   * @return 包含该文件的提交记录
   */
  List<FileRevision> getRevisions(int skip, int limit);

  // === 只读验证 ===
  /**
   * 验证操作是否被允许（历史文件为只读）
   * @param operation 操作类型
   * @return 是否允许
   */
  default boolean isOperationAllowed(FileOperation operation) {
    return !isHistorical() || operation.isReadOnly();
  }
}
