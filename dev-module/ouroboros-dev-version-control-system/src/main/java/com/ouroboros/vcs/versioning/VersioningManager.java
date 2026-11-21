package com.ouroboros.vcs.versioning;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import io.vavr.control.Try;

import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.file.FileDiff;
import com.ouroboros.vcs.file.FileRevision;

public interface VersioningManager {
  void destroy();

  VersionState getFileState(String filePath);

  Try<Boolean> commit(String filePath, String message);

  Try<Boolean> commit(Collection<String> filePath, String message);

  Try<Boolean> remove(String filePath, String message);

  Try<Boolean> remove(Collection<String> filePath, String message);

  Try<Boolean> move(String filePath, String newFilePath, String message);

  Try<Boolean> rollback(String filePath);

  OperationChain startChain();

  List<ChangeLog> getChangeLogs(String filePath);

  List<ChangeLog> getChangeLogs(Collection<String> filePath);

  List<ChangeLog> getChangeLogs(String filePath, int skip, int limit);

  List<ChangeLog> getChangeLogs(Collection<String> filePath, int skip, int limit);

  LocalDateTime getLastCommittedTime(String filePath);

  LocalDateTime getFirstCommittedTime(String filePath);

  List<String> getUncommittedFiles(String filePath);

  Try<Void> notifyFileChange(String... filePath);

  Try<Void> notifyFileChange(Collection<String> filePath);

  // 分支管理
  Try<String> createBranch(String branchName);

  Try<String> createBranch(String branchName, String fromRevision);

  Try<Boolean> deleteBranch(String branchName);

  Try<Boolean> switchBranch(String branchName);

  Try<List<String>> getBranchList();

  Try<String> getCurrentBranch();

  Try<Boolean> mergeBranch(String sourceBranch, String targetBranch, String message);

  // 仓库级远程配置与同步
  Try<String> getRemoteUri(String remoteName);

  Try<Boolean> setRemoteUri(String remoteName, String remoteUri);

  Try<Boolean> pull(String remoteName, String remoteBranch, VCSConfig.AuthConfig authConfig);

  Try<Boolean> push(String remoteName, String remoteBranch, VCSConfig.AuthConfig authConfig);

  Try<Boolean> isWorkspaceClean();

  // 标签管理
  Try<String> createTag(String tagName, String message);

  Try<String> createTag(String tagName, String revision, String message);

  Try<Boolean> deleteTag(String tagName);

  Try<List<Tag>> getTagList();

  // 文件列表获取
  Try<List<String>> getFileList();

  Try<List<String>> getFileList(String revision);

  Try<List<String>> getFileListFromBranch(String branchName);

  Try<List<String>> getFileListFromTag(String tagName);

  Try<List<String>> getFileList(String path, String revision);

  // 历史文件访问
  Try<String> getFileContent(String filePath, String revision);

  Try<byte[]> getFileBytes(String filePath, String revision);

  Try<String> getFileContentFromBranch(String filePath, String branchName);

  Try<String> getFileContentFromTag(String filePath, String tagName);

  // 文件元数据获取
  /**
   * 获取文件在指定版本的大小
   * @param filePath 文件路径
   * @param revision 版本标识
   * @return 文件大小
   */
  Try<Long> getFileSize(String filePath, String revision);

  // 文件差异比较
  /**
   * 比较文件在两个版本间的差异
   * @param filePath 文件路径
   * @param fromRevision 源版本
   * @param toRevision 目标版本
   * @return 差异信息
   */
  Try<FileDiff> compareFileRevisions(String filePath, String fromRevision, String toRevision);

  /**
   * 比较文件历史版本与工作区版本的差异
   * @param filePath 文件路径
   * @param revision 历史版本
   * @return 差异信息
   */
  Try<FileDiff> compareWithWorkingCopy(String filePath, String revision);

  // 文件版本历史
  /**
   * 获取文件的版本历史记录
   * @param filePath 文件路径
   * @return 版本历史列表
   */
  Try<List<FileRevision>> getFileRevisions(String filePath);

  /**
   * 获取文件的版本历史记录（分页）
   * @param filePath 文件路径
   * @param skip 跳过数量
   * @param limit 限制数量
   * @return 版本历史列表
   */
  Try<List<FileRevision>> getFileRevisions(String filePath, int skip, int limit);

  interface OperationChain {
    OperationChain add(String filePath);

    OperationChain add(Collection<String> filePath);

    OperationChain remove(String filePath);

    OperationChain remove(Collection<String> filePath);

    OperationChain rename(String filePath, String newFilePath);

    OperationChain user(String user);

    OperationChain email(String email);

    Try<String> commit(String message);
  }
}
