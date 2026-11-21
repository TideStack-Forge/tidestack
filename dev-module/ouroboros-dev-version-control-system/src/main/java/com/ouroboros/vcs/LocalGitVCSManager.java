package com.ouroboros.vcs;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;

import com.ouroboros.vcs.action.*;
import com.ouroboros.vcs.action.VCSAction.*;
import com.ouroboros.vcs.exception.MultipleFilesLockedException;
import com.ouroboros.vcs.file.DefaultVCSFile;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.filesystem.FileSystemManager;
import com.ouroboros.vcs.filesystem.LocalFileSystemManager;
import com.ouroboros.vcs.lock.LockManager;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.Tag;
import com.ouroboros.vcs.versioning.VersioningManager;
import com.ouroboros.vcs.versioning.git.GitManager;

public class LocalGitVCSManager implements VCSManager {
  static private final String EMPTY_COMMIT_MESSAGE = "<空>";
  static private final Logger LOGGER = LoggerFactory.getLogger(LocalGitVCSManager.class);

  private final VCSConfig config;
  private final LockManager lockManager;
  private final FileSystemManager fileSystemManager;
  private final VersioningManager versionControlManager;
  private final VCSUserProvider vcsUserProvider = VCSUserProvider.getInstance();
  private final VCSUserNameResolver vcsUserNameResolver = VCSUserNameResolver.getInstance();

  public LocalGitVCSManager(VCSConfig config, LockManager lockManager) {
    this.config = config;
    this.lockManager = lockManager;
    this.fileSystemManager = new LocalFileSystemManager(config.getRepositoryPath());
    this.versionControlManager = new GitManager(config.getRepositoryPath(), config.getDefaultBranch());
  }

  @Override
  public void destroy() {
    lockManager.destroy();
    fileSystemManager.destroy();
    versionControlManager.destroy();
  }

  @Override
  public VCSFile getFile(String relativePath) {
    return buildVcsFile(relativePath);
  }

  @Override
  public Try<Void> lock(String relativePath, boolean force) {
    return lock(Collections.singleton(relativePath), force);
  }

  @Override
  public Try<Void> lock(Collection<String> relativePaths, boolean force) {
    var lockUser = vcsUserProvider.getCurrentUser();
    var cannotLockFiles = relativePaths.stream()
        .map(f -> Tuple.of(f, lockManager.getLockOwner(f)))
        .filter(t -> StringUtils.isNotEmpty(t._2))
        .filter(t -> !lockUser.equals(t._2))
        .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2, (v1, v2) -> v2));
    if (!cannotLockFiles.isEmpty() && !force) {
      return Try.failure(new MultipleFilesLockedException(cannotLockFiles));
    }

    // unlock
    var unlockResult = lockManager.unlock(cannotLockFiles.keySet());

    return unlockResult.flatMap(ignore -> lockManager.lock(relativePaths, lockUser));
  }

  @Override
  public Try<Void> unlock(String relativePath, boolean force) {
    return unlock(Collections.singleton(relativePath), force);
  }

  @Override
  public Try<Void> unlock(Collection<String> relativePaths, boolean force) {
    if (force) {
      return lockManager.unlock(relativePaths);
    }
    var lockUser = vcsUserProvider.getCurrentUser();
    return lockManager.unlock(relativePaths, lockUser);
  }

  @Override
  public CreateFileAction createFile(String relativePath) {
    var file = buildVcsFile(relativePath);
    return new DefaultCreateFileAction(versionControlManager, fileSystemManager, lockManager, vcsUserProvider, file);
  }

  @Override
  public RemoveFileAction removeFile(String relativePath) {
    var file = buildVcsFile(relativePath);
    return new DefaultRemoveFileAction(versionControlManager, fileSystemManager, lockManager, vcsUserProvider, file);
  }

  @Override
  public UpdateFileAction updateFile(String relativePath) {
    var file = buildVcsFile(relativePath);
    return new DefaultUpdateFileAction(versionControlManager, fileSystemManager, lockManager, vcsUserProvider, file);
  }

  @Override
  public MoveFileAction moveFile(String relativePath, String newRelativePath) {
    return new DefaultMoveFileAction(versionControlManager, fileSystemManager, lockManager, vcsUserProvider)
        .from(relativePath)
        .to(newRelativePath);
  }

  @Override
  public BatchAction batchOperation() {
    return new DefaultBatchAction(versionControlManager, fileSystemManager, lockManager, vcsUserProvider);
  }

  @Override
  public Stream<VCSFile> walkDirectory(String relativeParentPath, String fileExtension) {
    Stream<VCSFile> stream = fileSystemManager.walk(relativeParentPath)
        .filter(fileExtension)
        .filter(File::isFile)
        .stream()
        .map(this::buildVcsFile);
    return stream
        .filter(Objects::nonNull);
  }

  @Override
  public List<ChangeLog> getChangeLogs(String relativePath) {
    return versionControlManager.getChangeLogs(relativePath);
  }

  @Override
  public List<ChangeLog> getChangeLogs(Collection<String> relativePaths) {
    return versionControlManager.getChangeLogs(relativePaths);
  }

  @Override
  public List<ChangeLog> getChangeLogs(String relativePath, int skip, int limit) {
    return versionControlManager.getChangeLogs(relativePath, skip, limit);
  }

  @Override
  public List<ChangeLog> getChangeLogs(Collection<String> relativePaths, int skip, int limit) {
    return versionControlManager.getChangeLogs(relativePaths, skip, limit);
  }

  @Override
  public List<VCSFile> getUncommittedFiles(String relativePath) {
    return versionControlManager.getUncommittedFiles(relativePath)
        .stream()
        .map(this::buildVcsFile)
        .collect(Collectors.toList());
  }

  // === 历史文件操作实现 ===

  @Override
  public VCSFile getFile(String relativePath, String revision) {
    return new DefaultVCSFile(versionControlManager, fileSystemManager, lockManager,
        vcsUserProvider, vcsUserNameResolver, relativePath, revision);
  }

  @Override
  public VCSFile getFileFromBranch(String relativePath, String branchName) {
    return getFile(relativePath, branchName);
  }

  @Override
  public VCSFile getFileFromTag(String relativePath, String tagName) {
    return getFile(relativePath, tagName);
  }

  @Override
  public List<VCSFile> getFileList(String relativeParentPath, String revision) {
    return versionControlManager.getFileList(relativeParentPath, revision)
        .map(files -> files.stream()
            .map(file -> getFile(file, revision))
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public List<VCSFile> getFileListFromBranch(String relativeParentPath, String branchName) {
    return versionControlManager.getFileListFromBranch(branchName)
        .map(files -> files.stream()
            .filter(file -> relativeParentPath.isEmpty() || file.startsWith(relativeParentPath))
            .map(file -> getFileFromBranch(file, branchName))
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public List<VCSFile> getFileListFromTag(String relativeParentPath, String tagName) {
    return versionControlManager.getFileListFromTag(tagName)
        .map(files -> files.stream()
            .filter(file -> relativeParentPath.isEmpty() || file.startsWith(relativeParentPath))
            .map(file -> getFileFromTag(file, tagName))
            .collect(Collectors.toList()))
        .getOrElse(Collections.emptyList());
  }

  @Override
  public Stream<VCSFile> walkDirectory(String relativeParentPath, String revision, String fileExtension) {
    return getFileList(relativeParentPath, revision).stream()
        .filter(file -> StringUtils.isEmpty(fileExtension) ||
            file.getFileName().endsWith(fileExtension));
  }

  private VCSFile buildVcsFile(String relativePath) {
    return new DefaultVCSFile(versionControlManager, fileSystemManager, lockManager, vcsUserProvider, vcsUserNameResolver, relativePath);
  }

  private VCSFile buildVcsFile(File file) {
    var absolutePath = file.getAbsolutePath();
    var repositoryPath = config.getRepositoryPath();
    if (!absolutePath.startsWith(repositoryPath)) {
      LOGGER.error("文件路径不在仓库目录下: {}, 仓库路径：{}", absolutePath, repositoryPath);
      return null;
    }
    var relativePath = absolutePath.substring(repositoryPath.length());
    if (StringUtils.isEmpty(relativePath) || relativePath.startsWith(".")) {
      return null;
    }
    return buildVcsFile(relativePath);
  }

  // === 标签管理实现 ===

  @Override
  public Try<String> createTag(String tagName, String message) {
    return versionControlManager.createTag(tagName, message);
  }

  @Override
  public Try<String> createTag(String tagName, String revision, String message) {
    return versionControlManager.createTag(tagName, revision, message);
  }

  @Override
  public Try<Boolean> deleteTag(String tagName) {
    return versionControlManager.deleteTag(tagName);
  }

  @Override
  public Try<List<Tag>> getTagList() {
    return versionControlManager.getTagList();
  }

  @Override
  public Try<String> getCurrentBranch() {
    return versionControlManager.getCurrentBranch();
  }

  @Override
  public Try<List<String>> getBranchList() {
    return versionControlManager.getBranchList();
  }

  @Override
  public Try<String> createBranch(String branchName) {
    return versionControlManager.createBranch(branchName);
  }

  @Override
  public Try<String> createBranch(String branchName, String fromRevision) {
    return versionControlManager.createBranch(branchName, fromRevision);
  }

  @Override
  public Try<Boolean> switchBranch(String branchName) {
    return versionControlManager.switchBranch(branchName);
  }

  @Override
  public Try<VCSConfig.RemoteConfig> getRemoteConfig() {
    var remoteConfig = config.getRemoteConfig();
    if (StringUtils.isBlank(remoteConfig.getRemoteUri())) {
      return versionControlManager.getRemoteUri(getRemoteName())
          .map(uri -> {
            remoteConfig.setRemoteUri(uri);
            return remoteConfig;
          });
    }
    return Try.success(remoteConfig);
  }

  @Override
  public Try<Boolean> setRemoteConfig(VCSConfig.RemoteConfig remoteConfig) {
    return versionControlManager.setRemoteUri(remoteConfig.getRemoteName(), remoteConfig.getRemoteUri())
        .map(result -> {
          if (Boolean.TRUE.equals(result)) {
            config.setRemoteConfig(remoteConfig);
          }
          return result;
        });
  }

  @Override
  public Try<Boolean> pull() {
    return versionControlManager.pull(getRemoteName(), getRemoteBranch(), config.getAuthConfig());
  }

  @Override
  public Try<Boolean> push() {
    return versionControlManager.push(getRemoteName(), getRemoteBranch(), config.getAuthConfig());
  }

  @Override
  public Try<Boolean> isWorkspaceClean() {
    return versionControlManager.isWorkspaceClean();
  }

  private String getRemoteName() {
    return StringUtils.defaultIfBlank(config.getRemoteName(), "origin");
  }

  private String getRemoteBranch() {
    return StringUtils.defaultIfBlank(config.getDefaultBranch(), "master");
  }

}
