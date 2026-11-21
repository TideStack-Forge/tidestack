package com.ouroboros.vcs;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import io.vavr.control.Try;

import com.ouroboros.vcs.action.VCSAction.*;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.Tag;

public interface VCSManager {
  void destroy();

  VCSFile getFile(String relativePath);

  Try<Void> lock(String relativePath, boolean force);

  Try<Void> lock(Collection<String> relativePaths, boolean force);

  Try<Void> unlock(String relativePath, boolean force);

  Try<Void> unlock(Collection<String> relativePaths, boolean force);

  CreateFileAction createFile(String relativePath);

  RemoveFileAction removeFile(String relativePath);

  UpdateFileAction updateFile(String relativePath);

  MoveFileAction moveFile(String relativePath, String newRelativePath);

  BatchAction batchOperation();

  Stream<VCSFile> walkDirectory(String relativeParentPath, String fileExtension);

  List<ChangeLog> getChangeLogs(String relativePath);

  List<ChangeLog> getChangeLogs(String relativePath, int skip, int limit);

  List<ChangeLog> getChangeLogs(Collection<String> relativePaths);

  List<ChangeLog> getChangeLogs(Collection<String> relativePaths, int skip, int limit);

  List<VCSFile> getUncommittedFiles(String relativePath);

  // === 历史文件操作 ===
  /**
   * 获取指定版本的文件
   * @param relativePath 文件相对路径
   * @param revision 版本标识（commit hash, 分支名, 标签名）
   * @return 历史版本文件对象
   */
  VCSFile getFile(String relativePath, String revision);

  /**
   * 获取指定分支的文件
   * @param relativePath 文件相对路径
   * @param branchName 分支名
   * @return 分支版本文件对象
   */
  VCSFile getFileFromBranch(String relativePath, String branchName);

  /**
   * 获取指定标签的文件
   * @param relativePath 文件相对路径
   * @param tagName 标签名
   * @return 标签版本文件对象
   */
  VCSFile getFileFromTag(String relativePath, String tagName);

  /**
   * 获取指定版本的目录文件列表
   * @param relativeParentPath 目录相对路径
   * @param revision 版本标识
   * @return 该版本下的文件列表
   */
  List<VCSFile> getFileList(String relativeParentPath, String revision);

  /**
   * 获取指定分支的目录文件列表
   * @param relativeParentPath 目录相对路径
   * @param branchName 分支名
   * @return 该分支下的文件列表
   */
  List<VCSFile> getFileListFromBranch(String relativeParentPath, String branchName);

  /**
   * 获取指定标签的目录文件列表
   * @param relativeParentPath 目录相对路径
   * @param tagName 标签名
   * @return 该标签下的文件列表
   */
  List<VCSFile> getFileListFromTag(String relativeParentPath, String tagName);

  /**
   * 遍历指定版本的目录树
   * @param relativeParentPath 目录相对路径
   * @param revision 版本标识
   * @param fileExtension 文件扩展名过滤器
   * @return 文件流
   */
  Stream<VCSFile> walkDirectory(String relativeParentPath, String revision, String fileExtension);

  // === 标签管理 ===
  /**
   * 创建标签
   * @param tagName 标签名
   * @param message 标签信息
   * @return 创建结果
   */
  Try<String> createTag(String tagName, String message);

  /**
   * 创建标签
   * @param tagName 标签名
   * @param revision 指定版本
   * @param message 标签信息
   * @return 创建结果
   */
  Try<String> createTag(String tagName, String revision, String message);

  /**
   * 删除标签
   * @param tagName 标签名
   * @return 删除结果
   */
  Try<Boolean> deleteTag(String tagName);

  /**
   * 获取所有标签列表
   * @return 标签列表
   */
  Try<List<Tag>> getTagList();

  // === 仓库级分支/远程/同步 ===
  Try<String> getCurrentBranch();

  Try<List<String>> getBranchList();

  Try<String> createBranch(String branchName);

  Try<String> createBranch(String branchName, String fromRevision);

  Try<Boolean> switchBranch(String branchName);

  Try<VCSConfig.RemoteConfig> getRemoteConfig();

  Try<Boolean> setRemoteConfig(VCSConfig.RemoteConfig remoteConfig);

  Try<Boolean> pull();

  Try<Boolean> push();

  Try<Boolean> isWorkspaceClean();
}
