package com.ouroboros.vcs.versioning.git;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.vavr.control.Try;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.vcs.VCSUserEmailResolver;
import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.Tag;
import com.ouroboros.vcs.versioning.VersionState;
import com.ouroboros.vcs.versioning.VersioningManager;
import com.ouroboros.vcs.file.FileDiff;
import com.ouroboros.vcs.file.FileRevision;

public class GitManager implements VersioningManager {

  private static final String CACHE_NAME_TEMPLATE = "vcs-%s:%s";

  private final String repositoryPath;
  private final String defaultBranch;
  private final VCSUserEmailResolver userEmailResolver;
  private final VCSUserProvider userProvider;
  private final CacheManager cacheManager;
  private final Set<String> cacheNames = ConcurrentHashMap.newKeySet();

  public GitManager(String repositoryPath, String defaultBranch) {
    this.repositoryPath = repositoryPath;
    this.defaultBranch = StringUtils.isBlank(defaultBranch) ? "master" : defaultBranch;
    userEmailResolver = VCSUserEmailResolver.getInstance();
    userProvider = VCSUserProvider.getInstance();
    cacheManager = CacheManager.getCacheManager();
  }

  @Override
  public void destroy() {
  }

  @Override
  public VersionState getFileState(String filePath) {
    var state = calcWithCache(filePath, "state", () -> {
      var client = getJGitClient();

      var isTracked = client.isTracked(filePath);
      if (isTracked.isFailure()) {
        return VersionState.UNKNOWN;
      }
      if (!isTracked.get()) {
        return VersionState.UNVERSIONED;
      }

      var isChanged = client.isChanged(filePath);
      if (isChanged.isFailure()) {
        return VersionState.UNKNOWN;
      }
      return isChanged.get() ? VersionState.CHANGED : VersionState.NOTCHANGED;
    }).get();
    return state;
  }

  @Override
  public LocalDateTime getLastCommittedTime(String filePath) {
    var result = calcWithCache(filePath, "lastCommittedTime", () -> {
      var client = getJGitClient();
      return client.getLastCommittedTime(filePath)
          .andFinally(toDestroyClient(client))
          .getOrElse(LocalDateTime.MIN);
    }).get();
    return LocalDateTime.MIN.equals(result) ? null : result;
  }

  @Override
  public LocalDateTime getFirstCommittedTime(String filePath) {
    var result = calcWithCache(filePath, "firstCommittedTime", () -> {
      var client = getJGitClient();
      return client.getCreationTime(filePath)
          .andFinally(toDestroyClient(client))
          .getOrElse(LocalDateTime.MIN);
    }).get();
    return LocalDateTime.MIN.equals(result) ? null : result;
  }

  @Override
  public Try<Boolean> commit(String filePath, String message) {
    return startChain()
        .add(filePath)
        .commit(message)
        .map(StringUtils::isNotEmpty);
  }

  @Override
  public Try<Boolean> commit(Collection<String> filePath, String message) {
    return startChain()
        .add(filePath)
        .commit(message)
        .map(StringUtils::isNotEmpty);
  }

  @Override
  public Try<Boolean> remove(String filePath, String message) {
    return startChain()
        .remove(filePath)
        .commit(message)
        .map(StringUtils::isNotEmpty);
  }

  @Override
  public Try<Boolean> remove(Collection<String> filePath, String message) {
    return startChain()
        .remove(filePath)
        .commit(message)
        .map(StringUtils::isNotEmpty);
  }

  @Override
  public Try<Boolean> move(String filePath, String newFilePath, String message) {
    return startChain()
        .remove(filePath)
        .add(newFilePath)
        .commit(message)
        .map(StringUtils::isNotEmpty);
  }

  @Override
  public Try<Boolean> rollback(String filePath) {
    var client = getJGitClient();
    var isTracked = client.isTracked(filePath).getOrElse(false);
    var isChanged = client.isChanged(filePath).getOrElse(false);
    if (!isTracked || !isChanged) {
      return Try.success(false);
    }

    return client.rollback(filePath)
        .andFinally(toDestroyClient(client))
        .andFinally(toClearCache(filePath))
        .map(v -> true);
  }

  @Override
  public List<ChangeLog> getChangeLogs(String filePath) {
    var result = calcWithCache(filePath, "changeLog", () -> {
      var client = getJGitClient();
      return client.getChangeLogs(filePath)
          .andFinally(toDestroyClient(client))
          .getOrElse(Collections::emptyList);
    }).get();
    return result;
  }

  @Override
  public List<ChangeLog> getChangeLogs(String filePath, int skip, int limit) {
    var key = String.format("changeLog-%s-%s", skip, limit);
    var result = calcWithCache(filePath, key, () -> {
      var client = getJGitClient();
      return client.getChangeLogs(filePath, skip, limit)
          .andFinally(toDestroyClient(client))
          .getOrElse(Collections::emptyList);
    }).get();
    return result;
  }

  @Override
  public List<ChangeLog> getChangeLogs(Collection<String> filePath) {
    var client = getJGitClient();
    return client.getChangeLogs(filePath)
        .andFinally(toDestroyClient(client))
        .getOrElse(Collections::emptyList);
  }

  @Override
  public List<ChangeLog> getChangeLogs(Collection<String> filePath, int skip, int limit) {
    var client = getJGitClient();
    return client.getChangeLogs(filePath, skip, limit)
        .andFinally(toDestroyClient(client))
        .getOrElse(Collections::emptyList);
  }

  @Override
  public OperationChain startChain() {
    return new OperationChainImpl();
  }

  @Override
  public List<String> getUncommittedFiles(String filePath) {
    var status = getJGitClient().getStatus();
    if (status.isFailure()) {
      return Collections.emptyList();
    }

    var untracked = status.get().getUntracked();
    var modified = status.get().getModified();
    var removed = status.get().getRemoved();
    var added = status.get().getAdded();
    var changed = status.get().getChanged();
    var missing = status.get().getMissing();
    var result = new ArrayList<>(untracked);
    result.addAll(modified);
    result.addAll(removed);
    result.addAll(added);
    result.addAll(changed);
    result.addAll(missing);
    if (StringUtils.isBlank(filePath) || ".".equals(filePath) || "./".equals(filePath)) {
      return result;
    }
    return result.stream()
        .filter(f -> StringUtils.startsWithIgnoreCase(f, filePath))
        .collect(Collectors.toList());
  }

  @Override
  public Try<Void> notifyFileChange(String... filePath) {
    if (filePath == null || filePath.length == 0) {
      return Try.success(null);
    }
    return Try.run(this::clearRepositoryCaches);
  }

  @Override
  public Try<Void> notifyFileChange(Collection<String> filePath) {
    return Try.run(this::clearRepositoryCaches);
  }

  private JGitClient getJGitClient() {
    return new JGitClient(repositoryPath, defaultBranch);
  }

  private Runnable toDestroyClient(JGitClient client) {
    return client::close;
  }

  // 生成缓存名称
  private String getCacheName(String filePath) {
    return String.format(CACHE_NAME_TEMPLATE, repositoryPath, filePath);
  }

  // 包裹函数，使用缓存
  private <T> Supplier<T> calcWithCache(String filePath, String key, Supplier<T> supplier) {
    var cacheName = getCacheName(filePath);
    cacheNames.add(cacheName);
    return () -> cacheManager.getOrPut(cacheName, key, supplier);
  }

  public void clearFileCache(String filePath) {
    cacheManager.clear(getCacheName(filePath));
  }

  public void clearFileCache(Collection<String> filePath) {
    filePath.forEach(this::clearFileCache);
  }

  public void clearRepositoryCaches() {
    cacheNames.forEach(cacheManager::clear);
  }

  private Runnable toClearCache(String filePath) {
    return this::clearRepositoryCaches;
  }

  @SafeVarargs
  private Runnable toClearCaches(Collection<String>... filePath) {
    return this::clearRepositoryCaches;
  }

  // 分支管理实现
  @Override
  public Try<String> createBranch(String branchName) {
    var client = getJGitClient();
    return client.createBranch(branchName)
        .andFinally(toDestroyClient(client))
        .andFinally(this::clearRepositoryCaches);
  }

  @Override
  public Try<String> createBranch(String branchName, String fromRevision) {
    var client = getJGitClient();
    return client.createBranch(branchName, fromRevision)
        .andFinally(toDestroyClient(client))
        .andFinally(this::clearRepositoryCaches);
  }

  @Override
  public Try<Boolean> deleteBranch(String branchName) {
    var client = getJGitClient();
    return client.deleteBranch(branchName)
        .andFinally(toDestroyClient(client))
        .andFinally(this::clearRepositoryCaches);
  }

  @Override
  public Try<Boolean> switchBranch(String branchName) {
    var client = getJGitClient();
    return client.switchBranch(branchName)
        .andFinally(toDestroyClient(client))
        .andFinally(this::clearRepositoryCaches);
  }

  @Override
  public Try<List<String>> getBranchList() {
    var result = calcWithCache("repo", "branches", () -> {
      var client = getJGitClient();
      return client.listBranches()
          .andFinally(toDestroyClient(client))
          .getOrElse(Collections::emptyList);
    }).get();
    return Try.success(result);
  }

  @Override
  public Try<String> getCurrentBranch() {
    var client = getJGitClient();
    return client.getCurrentBranch()
        .andFinally(toDestroyClient(client));
  }

  @Override
  public Try<Boolean> mergeBranch(String sourceBranch, String targetBranch, String message) {
    var client = getJGitClient();
    return client.mergeBranch(sourceBranch, targetBranch, message)
        .andFinally(toDestroyClient(client))
        .andFinally(this::clearRepositoryCaches);
  }

  @Override
  public Try<String> getRemoteUri(String remoteName) {
    var client = getJGitClient();
    return client.getRemoteUri(remoteName)
        .andFinally(toDestroyClient(client));
  }

  @Override
  public Try<Boolean> setRemoteUri(String remoteName, String remoteUri) {
    var client = getJGitClient();
    return client.setRemoteUri(remoteName, remoteUri)
        .andFinally(toDestroyClient(client))
        .andFinally(this::clearRepositoryCaches);
  }

  @Override
  public Try<Boolean> pull(String remoteName, String remoteBranch, VCSConfig.AuthConfig authConfig) {
    var client = getJGitClient();
    return client.pull(remoteName, remoteBranch, authConfig)
        .andFinally(toDestroyClient(client))
        .andFinally(this::clearRepositoryCaches)
        .map(ignored -> true);
  }

  @Override
  public Try<Boolean> push(String remoteName, String remoteBranch, VCSConfig.AuthConfig authConfig) {
    var client = getJGitClient();
    return client.push(remoteName, remoteBranch, authConfig)
        .andFinally(toDestroyClient(client))
        .andFinally(this::clearRepositoryCaches)
        .map(ignored -> true);
  }

  @Override
  public Try<Boolean> isWorkspaceClean() {
    var client = getJGitClient();
    return client.isWorkspaceClean()
        .andFinally(toDestroyClient(client));
  }

  // 标签管理实现
  @Override
  public Try<String> createTag(String tagName, String message) {
    var client = getJGitClient();
    var user = VCSUserProvider.getInstance().getCurrentUser();
    var email = VCSUserEmailResolver.getInstance().resolveUserEmail(user);
    return client.createTag(tagName, message, user, email)
        .andFinally(toDestroyClient(client))
        .andFinally(this::clearRepositoryCaches);
  }

  @Override
  public Try<String> createTag(String tagName, String revision, String message) {
    var client = getJGitClient();
    var user = VCSUserProvider.getInstance().getCurrentUser();
    var email = VCSUserEmailResolver.getInstance().resolveUserEmail(user);
    return client.createTag(tagName, revision, message, user, email)
        .andFinally(toDestroyClient(client))
        .andFinally(this::clearRepositoryCaches);
  }

  @Override
  public Try<Boolean> deleteTag(String tagName) {
    var client = getJGitClient();
    return client.deleteTag(tagName)
        .andFinally(toDestroyClient(client))
        .andFinally(this::clearRepositoryCaches);
  }

  @Override
  public Try<List<Tag>> getTagList() {
    var result = calcWithCache("repo", "tags", () -> {
      var client = getJGitClient();
      return client.listTags()
          .andFinally(toDestroyClient(client))
          .getOrElse(Collections::emptyList);
    }).get();
    return Try.success(result);
  }

  // 文件列表获取实现
  @Override
  public Try<List<String>> getFileList() {
    var result = calcWithCache("repo", "fileList-HEAD", () -> {
      var client = getJGitClient();
      return client.getFileList()
          .andFinally(toDestroyClient(client))
          .getOrElse(Collections::emptyList);
    }).get();
    return Try.success(result);
  }

  @Override
  public Try<List<String>> getFileList(String revision) {
    var result = calcWithCache("repo", "fileList-" + revision, () -> {
      var client = getJGitClient();
      return client.getFileList(revision)
          .andFinally(toDestroyClient(client))
          .getOrElse(Collections::emptyList);
    }).get();
    return Try.success(result);
  }

  @Override
  public Try<List<String>> getFileListFromBranch(String branchName) {
    var result = calcWithCache("repo", "fileList-branch-" + branchName, () -> {
      var client = getJGitClient();
      return client.getFileListFromBranch(branchName)
          .andFinally(toDestroyClient(client))
          .getOrElse(Collections::emptyList);
    }).get();
    return Try.success(result);
  }

  @Override
  public Try<List<String>> getFileListFromTag(String tagName) {
    var result = calcWithCache("repo", "fileList-tag-" + tagName, () -> {
      var client = getJGitClient();
      return client.getFileListFromTag(tagName)
          .andFinally(toDestroyClient(client))
          .getOrElse(Collections::emptyList);
    }).get();
    return Try.success(result);
  }

  @Override
  public Try<List<String>> getFileList(String path, String revision) {
    var cacheKey = String.format("fileList-%s-%s", path != null ? path : "root", revision);
    var result = calcWithCache("repo", cacheKey, () -> {
      var client = getJGitClient();
      return client.getFileList(path, revision)
          .andFinally(toDestroyClient(client))
          .getOrElse(Collections::emptyList);
    }).get();
    return Try.success(result);
  }

  // 历史文件内容访问实现
  @Override
  public Try<String> getFileContent(String filePath, String revision) {
    var cacheKey = String.format("fileContent-%s-%s", filePath, revision);
    var result = calcWithCache(filePath, cacheKey, () -> {
      var client = getJGitClient();
      return client.getFileContent(filePath, revision)
          .andFinally(toDestroyClient(client))
          .getOrElse(() -> null);
    }).get();
    return Try.success(result);
  }

  @Override
  public Try<byte[]> getFileBytes(String filePath, String revision) {
    // 字节内容不缓存，避免内存压力
    var client = getJGitClient();
    return client.getFileBytes(filePath, revision)
        .andFinally(toDestroyClient(client));
  }

  @Override
  public Try<String> getFileContentFromBranch(String filePath, String branchName) {
    var cacheKey = String.format("fileContent-%s-branch-%s", filePath, branchName);
    var result = calcWithCache(filePath, cacheKey, () -> {
      var client = getJGitClient();
      return client.getFileContentFromBranch(filePath, branchName)
          .andFinally(toDestroyClient(client))
          .getOrElse(() -> null);
    }).get();
    return Try.success(result);
  }

  @Override
  public Try<String> getFileContentFromTag(String filePath, String tagName) {
    var cacheKey = String.format("fileContent-%s-tag-%s", filePath, tagName);
    var result = calcWithCache(filePath, cacheKey, () -> {
      var client = getJGitClient();
      return client.getFileContentFromTag(filePath, tagName)
          .andFinally(toDestroyClient(client))
          .getOrElse(() -> null);
    }).get();
    return Try.success(result);
  }

  // === 新增方法实现 ===

  @Override
  public Try<Long> getFileSize(String filePath, String revision) {
    return Try.of(() -> {
      var client = getJGitClient();
      return client.getFileBytes(filePath, revision)
          .map(bytes -> (long) bytes.length)
          .andFinally(toDestroyClient(client))
          .get();
    });
  }


  @Override
  public Try<FileDiff> compareFileRevisions(String filePath, String fromRevision, String toRevision) {
    return Try.failure(new UnsupportedOperationException("File revision comparison not yet implemented"));
  }

  @Override
  public Try<FileDiff> compareWithWorkingCopy(String filePath, String revision) {
    return Try.failure(new UnsupportedOperationException("Working copy comparison not yet implemented"));
  }

  @Override
  public Try<List<FileRevision>> getFileRevisions(String filePath) {
    return getFileRevisions(filePath, 0, Integer.MAX_VALUE);
  }

  @Override
  public Try<List<FileRevision>> getFileRevisions(String filePath, int skip, int limit) {
    return Try.of(() -> {
      var changeLogs = getChangeLogs(filePath, skip, limit);
      return changeLogs.stream()
          .map(this::toFileRevision)
          .collect(Collectors.toList());
    });
  }

  private FileRevision toFileRevision(ChangeLog changeLog) {
    long fileSize = getFileSize("", changeLog.getId()).getOrElse(0L);
    return new FileRevision(
        changeLog.getId(),
        changeLog.getAuthor(),
        changeLog.getTime(),
        changeLog.getMessage(),
        fileSize,
        FileRevision.RevisionType.COMMIT
    );
  }

  /**
   * 操作链
   * 链式指定参数，最后 commit 的时候才真正执行
   */
  class OperationChainImpl implements OperationChain {
    Set<String> addedFiles = new HashSet<>();
    Set<String> removedFiles = new HashSet<>();
    String user = null;
    String email = null;

    @Override
    public OperationChain add(String filePath) {
      addedFiles.add(filePath);
      removedFiles.remove(filePath);
      return this;
    }

    @Override
    public OperationChain add(Collection<String> filePath) {
      addedFiles.addAll(filePath);
      removedFiles.removeAll(filePath);
      return this;
    }

    @Override
    public OperationChain remove(String filePath) {
      removedFiles.add(filePath);
      addedFiles.remove(filePath);
      return this;
    }

    @Override
    public OperationChain remove(Collection<String> filePath) {
      removedFiles.addAll(filePath);
      addedFiles.removeAll(filePath);
      return this;
    }

    @Override
    public OperationChain rename(String filePath, String newFilePath) {
      add(newFilePath);
      remove(filePath);
      return this;
    }

    @Override
    public OperationChain user(String user) {
      this.user = user;
      return this;
    }

    @Override
    public OperationChain email(String email) {
      this.email = email;
      return this;
    }

    @Override
    public Try<String> commit(String message) {
      var client = getJGitClient();
      String finalUser = user != null ? user : userProvider.getCurrentUser();
      String finalEmail = StringUtils.isNotEmpty(email)
          // 如果设置了 email 则使用设置的
          ? email
          // 如果设置过 user 则使用 emailResolver 获取 email;
          : user != null
          ? userEmailResolver.resolveUserEmail(user)
          // 否则获取当前用户的 email
          : userProvider.getCurrentUserEmail();

      // 提交
      var result = client
          .add(addedFiles)
          .remove(removedFiles)
          .commit(finalUser, finalEmail, message);

      // 无论成功与否都要清理缓存
      return result
          .andFinally(toDestroyClient(client))
          .andFinally(toClearCaches(addedFiles, removedFiles));
    }
  }
}
