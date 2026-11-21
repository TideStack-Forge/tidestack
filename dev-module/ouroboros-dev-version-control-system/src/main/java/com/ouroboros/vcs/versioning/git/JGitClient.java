package com.ouroboros.vcs.versioning.git;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.Tuple;
import io.vavr.control.Try;

import com.ouroboros.vcs.VCSConfig;
import com.ouroboros.util.Dates;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.Tag;


/**
 * The GitUtil
 */
@SuppressWarnings("unused")
public class JGitClient implements AutoCloseable {
  private static final String SYSTEM_USERNAME = "system";
  private static final String EMBED_USER_IN_MESSAGE_PREFIX = " 操作人:";
  private static final String EMPTY_COMMIT_MESSAGE = "<空>";
  private static final Logger LOGGER = LoggerFactory.getLogger(JGitClient.class);

  private final String branch;
  private final Git jGit;
  private final Set<String> addedFiles;
  private final Set<String> removedFiles;

  private Status status;

  JGitClient(String repositoryPath, String branch) {
    this.branch = branch == null || branch.isEmpty() ? "master" : branch;
    addedFiles = new HashSet<>();
    removedFiles = new HashSet<>();
    jGit = initGit(repositoryPath);
  }

  private Git initGit(String repositoryPath) {
    try {
      var gitPath = Paths.get(repositoryPath).toFile();
      var gitFile = Paths.get(repositoryPath, ".git").toFile();
      // 仓库目录不存在则创建目录
      if (!gitPath.exists()) {
        LOGGER.info("Git repository not found, create repository.");
        if (!gitPath.mkdirs()) {
          LOGGER.error("Create repository failed! path: " + repositoryPath);
        }
      }
      // 未初始化的仓库，需要先初始化
      if (!gitFile.exists()) {
        LOGGER.info("Git repository not initialized, init repository.");
        return Git.init()
            .setDirectory(new File(repositoryPath))
            .setInitialBranch(branch)
            .call();
      }
      return Git.open(gitPath);
    } catch (IOException e) {
      LOGGER.error("Repository can not access! path: {}", repositoryPath, e);
    } catch (GitAPIException e) {
      LOGGER.error("Git repository not initialized! path: {}", repositoryPath, e);
    }
    return null;
  }

  @Override
  public void close() {
    Try.run(jGit::close)
        .andFinally(this::clear);
  }

  public JGitClient clear() {
    addedFiles.clear();
    removedFiles.clear();
    return this;
  }

  public Try<Status> getStatus() {
    if (status == null) {
      var tryStatus = Try.of(() -> jGit.status().call());
      if (tryStatus.isFailure()) {
        return tryStatus;
      }
      status = tryStatus.get();
    }
    return Try.success(status);
  }

  private void clearStatus() {
    status = null;
  }

  public JGitClient add(String filePath) {
    String normalizedPath = normalizePath(filePath);
    addedFiles.add(normalizedPath);
    removedFiles.remove(normalizedPath);
    return this;
  }

  public JGitClient add(Collection<String> filePaths) {
    var normalizedPaths = filePaths.stream().map(this::normalizePath).collect(Collectors.toSet());
    addedFiles.addAll(normalizedPaths);
    removedFiles.removeAll(normalizedPaths);
    return this;
  }

  public JGitClient remove(String filePath) {
    String normalizedPath = normalizePath(filePath);
    removedFiles.add(normalizedPath);
    addedFiles.remove(normalizedPath);
    return this;
  }

  public JGitClient remove(Collection<String> filePaths) {
    var normalizedPaths = filePaths.stream().map(this::normalizePath).collect(Collectors.toSet());
    removedFiles.addAll(normalizedPaths);
    addedFiles.removeAll(normalizedPaths);
    return this;
  }

  public JGitClient move(String oldPath, String newPath) {
    return remove(oldPath)
        .add(newPath);
  }

  public Try<String> commit(String user, String email, String msg) {
    String tmpMsg = msg == null ? EMPTY_COMMIT_MESSAGE : msg;
    var result = Try.of(() -> {
      // 检查所有需要删除的文件是否已经被跟踪
      var statusOp = getStatus();
      if (statusOp.isFailure()) {
        LOGGER.error("Get git status failed! Git repository: {}", jGit.getRepository().getWorkTree().getName());
        throw statusOp.getCause();
      }
      var status = statusOp.get();
      var missed = status.getMissing();
      // 删除不在 missed 中的文件
      removedFiles.removeIf((v) -> !missed.contains(v));

      // 检查所有需要添加的文件是否需要提交
      var changed = status.getChanged();
      var modified = status.getModified();
      var untracked = status.getUntracked();

      // 删除不在 changed、modified、untracked 中的文件
      addedFiles.removeIf((v) -> !changed.contains(v) && !modified.contains(v) && !untracked.contains(v));

      if (addedFiles.isEmpty() && removedFiles.isEmpty()) {
        return "";
      }

      Set<String> selectedPaths = new HashSet<>(addedFiles);
      selectedPaths.addAll(removedFiles);
      var before = jGit.getRepository().readDirCache();
      var originalEntries = new ArrayList<DirCacheEntry>();
      for (int index = 0; index < before.getEntryCount(); index++) {
        var entry = before.getEntry(index);
        if (selectedPaths.contains(entry.getPathString())) { originalEntries.add(new DirCacheEntry(entry)); }
      }
      return Try.of(() -> {

        // 删除文件
        if (!removedFiles.isEmpty()) {
          var remove = jGit.rm();
          removedFiles.forEach(remove::addFilepattern);
          remove.call();
        }

        // 添加文件
        if (!addedFiles.isEmpty()) {
          var add = jGit.add();
          addedFiles.forEach(add::addFilepattern);
          add.call();
        }

        // 提交
        var commit = jGit.commit();
        selectedPaths.forEach(commit::setOnly);
        String finalMsg;

        // 如果用户和Email都有值则为commit设置作者
        if (!StringUtils.isEmpty(user) && !StringUtils.isEmpty(email)) {
          finalMsg = tmpMsg;
          commit.setAuthor(user, email);
          commit.setCommitter(user, email);
        }
        // 如果提交者信息不全则用默认提交者信息，并把提交者追加到提交信息中
        else if (!StringUtils.isEmpty(user)) {
          finalMsg = tmpMsg + EMBED_USER_IN_MESSAGE_PREFIX + user;
        }
        // 如果没有提交者信息，则标记为系统用户
        else {
          finalMsg = tmpMsg + EMBED_USER_IN_MESSAGE_PREFIX + SYSTEM_USERNAME;
        }
        var rev = commit.setMessage(finalMsg).call();
        return rev.getName();
      }).onFailure(failure -> restoreIndex(selectedPaths, originalEntries, failure)).get();
    }).andFinally(this::clear);

    return result;
  }

  private void restoreIndex(Set<String> selectedPaths, List<DirCacheEntry> originals, Throwable failure) {
    Try.run(() -> {
      var index = jGit.getRepository().lockDirCache();
      try {
        var entries = new ArrayList<DirCacheEntry>(originals);
        for (int offset = 0; offset < index.getEntryCount(); offset++) {
          var entry = index.getEntry(offset);
          if (!selectedPaths.contains(entry.getPathString())) { entries.add(new DirCacheEntry(entry)); }
        }
        entries.sort((first, second) -> {
          int pathOrder = Arrays.compareUnsigned(first.getRawPath(), second.getRawPath());
          return pathOrder == 0 ? Integer.compare(first.getStage(), second.getStage()) : pathOrder;
        });
        var builder = index.builder();
        entries.forEach(builder::add);
        if (!builder.commit()) { throw new IOException("无法恢复 Git 暂存区"); }
      } finally {
        index.unlock();
      }
    }).onFailure(failure::addSuppressed);
  }

  public Try<Boolean> isTracked(String path) {
    var result = getStatus().map(s -> !s.getUntracked().contains(path));
    return result;
  }

  public Try<Boolean> isChanged(String path) {
    var result = getStatus().map((status) -> status.getUntracked().contains(path)
        || status.getChanged().contains(path)
        || status.getRemoved().contains(path)
        || status.getModified().contains(path)
        || status.getMissing().contains(path));
    return result;
  }

  public Try<JGitClient> pull(String remoteUri, String remoteBranch) {
    var result = Try.run(() -> {
      validatePullResult(jGit.pull()
        .setRemote(remoteUri)
        .setRemoteBranchName(remoteBranch)
        .call());
      clearStatus();
    });
    return result.map((v) -> this);
  }

  public Try<JGitClient> pull(String remoteName, String remoteBranch, VCSConfig.AuthConfig authConfig) {
    var result = Try.run(() -> {
      PullCommand command = jGit.pull()
          .setRemote(remoteName)
          .setRemoteBranchName(remoteBranch);
      applyAuthConfig(command, authConfig);
      validatePullResult(command.call());
      clearStatus();
    });
    return result.map((v) -> this);
  }

  public Try<JGitClient> push(String remoteName, String remoteBranch) {
    var result = Try.run(() -> {
      String localBranch = jGit.getRepository().getBranch();
      Iterable<PushResult> pushResults = jGit.push()
          .setRemote(remoteName)
          .setRefSpecs(new RefSpec("refs/heads/" + localBranch + ":refs/heads/" + remoteBranch))
          .call();
      validatePushResults(pushResults, remoteBranch);
      clearStatus();
    });
    return result.map((v) -> this);
  }

  public Try<JGitClient> push(String remoteName, String remoteBranch, VCSConfig.AuthConfig authConfig) {
    var result = Try.run(() -> {
      String localBranch = jGit.getRepository().getBranch();
      var command = jGit.push()
          .setRemote(remoteName)
          .setRefSpecs(new RefSpec("refs/heads/" + localBranch + ":refs/heads/" + remoteBranch));
      applyAuthConfig(command, authConfig);
      validatePushResults(command.call(), remoteBranch);
      clearStatus();
    });
    return result.map((v) -> this);
  }

  public Try<String> getRemoteUri(String remoteName) {
    return Try.of(() -> jGit.getRepository().getConfig().getString("remote", remoteName, "url"));
  }

  public Try<Boolean> setRemoteUri(String remoteName, String remoteUri) {
    return Try.of(() -> {
      var config = jGit.getRepository().getConfig();
      config.setString("remote", remoteName, "url", remoteUri);
      config.setString("remote", remoteName, "fetch", "+refs/heads/*:refs/remotes/" + remoteName + "/*");
      config.save();
      return true;
    });
  }

  public Try<Boolean> isWorkspaceClean() {
    return getStatus().map(Status::isClean);
  }

  void applyAuthConfig(TransportCommand<?, ?> command, VCSConfig.AuthConfig authConfig) {
    if (authConfig == null || authConfig.getAuthType() == null || authConfig.getAuthType() == VCSConfig.AuthType.NONE) {
      return;
    }
    if (authConfig.getAuthType() == VCSConfig.AuthType.SSH_KEY && StringUtils.isNotBlank(authConfig.getSshKeyPath())) {
      command.setTransportConfigCallback(new TransportConfig(authConfig.getSshKeyPath()));
      return;
    }
    if (authConfig.getAuthType() == VCSConfig.AuthType.HTTP_BASIC && StringUtils.isNotBlank(authConfig.getUsername())) {
      CredentialsProvider provider = new UsernamePasswordCredentialsProvider(
          authConfig.getUsername(),
          StringUtils.defaultString(authConfig.getPassword()));
      command.setCredentialsProvider(provider);
    }
  }

  static void validatePullResult(PullResult pullResult) {
    if (pullResult != null && pullResult.isSuccessful()) {
      return;
    }
    String message = "Pull failed";
    if (pullResult != null) {
      MergeResult mergeResult = pullResult.getMergeResult();
      if (mergeResult != null && mergeResult.getMergeStatus() != null) {
        message = "Pull failed: " + mergeResult.getMergeStatus();
      } else if (pullResult.getRebaseResult() != null && pullResult.getRebaseResult().getStatus() != null) {
        message = "Pull failed: " + pullResult.getRebaseResult().getStatus();
      } else if (StringUtils.isNotBlank(pullResult.toString())) {
        message = "Pull failed: " + pullResult;
      }
    }
    throw new IllegalStateException(message);
  }

  static void validatePushResults(Iterable<PushResult> pushResults, String remoteBranch) {
    List<String> failures = new ArrayList<>();
    for (PushResult pushResult : pushResults) {
      for (RemoteRefUpdate update : pushResult.getRemoteUpdates()) {
        RemoteRefUpdate.Status status = update.getStatus();
        if (status == RemoteRefUpdate.Status.OK || status == RemoteRefUpdate.Status.UP_TO_DATE) {
          continue;
        }
        StringBuilder message = new StringBuilder();
        message.append(update.getRemoteName()).append(" -> ").append(status);
        if (StringUtils.isNotBlank(update.getMessage())) {
          message.append(" (").append(update.getMessage()).append(")");
        }
        failures.add(message.toString());
      }
      if (StringUtils.isNotBlank(pushResult.getMessages())) {
        failures.add(pushResult.getMessages().trim());
      }
    }
    if (!failures.isEmpty()) {
      throw new IllegalStateException("Push to " + remoteBranch + " failed: " + String.join("; ", failures));
    }
  }

  public Try<JGitClient> rollback(String filePath) {
    var result = Try.run(() -> jGit.checkout()
        .setAllPaths(false)
        .addPath(filePath)
        .setCreateBranch(false)
        .call());
    return result.map((v) -> this);
  }

  public Try<JGitClient> rollback(Collection<String> filePaths) {
    var fileList = filePaths instanceof List<?> ? (List<String>) filePaths : new ArrayList<>(filePaths);
    var result = Try.run(() -> jGit.checkout()
        .setAllPaths(false)
        .addPaths(fileList)
        .setCreateBranch(false)
        .call());
    return result.map((v) -> this);
  }

  public Try<LocalDateTime> getCreationTime(String path) {
    var result = Try.of(() -> {
      var head = jGit.getRepository().resolve(Constants.HEAD);
      RevWalk revWalk = new RevWalk(jGit.getRepository());
      revWalk.sort(RevSort.REVERSE);
      revWalk.setTreeFilter(PathFilter.create(path));
      revWalk.markStart(revWalk.parseCommit(head));
      var iterator = revWalk.iterator();
      if (!iterator.hasNext()) {
        return LocalDateTime.MIN;
      }
      var commit = iterator.next();
      return Dates.toLocalDateTime(commit.getCommitterIdent().getWhen());
    });
    return result;
  }

  public Try<LocalDateTime> getLastCommittedTime(String path) {
    var result = Try.of(() -> {
      var head = jGit.getRepository().resolve(Constants.HEAD);
      var lastCommitIterator = jGit
          .log()
          .add(head)
          .addPath(path)
          .setMaxCount(1)
          .call()
          .iterator();
      if (!lastCommitIterator.hasNext()) {
        return LocalDateTime.MIN;
      }
      return Dates.toLocalDateTime(lastCommitIterator.next().getCommitTime());
    });
    return result;
  }

  public Try<List<ChangeLog>> getChangeLogs(String path) {
    if (StringUtils.isEmpty(path) || ".".equals(path) || "./".equals(path)) {
      return getChangeLogs(Collections.emptyList());
    }
    return getChangeLogs(Collections.singleton(path));
  }

  public Try<List<ChangeLog>> getChangeLogs(String path, int skip, int limit) {
    if (StringUtils.isEmpty(path) || ".".equals(path) || "./".equals(path)) {
      return getChangeLogs(Collections.emptyList(), skip, limit);
    }
    return getChangeLogs(Collections.singleton(path), skip, limit);
  }

  public Try<List<ChangeLog>> getChangeLogs(Collection<String> paths) {
    Try<List<ChangeLog>> result = Try.of(() -> {
      var repository = jGit.getRepository();
      var head = repository.resolve(Constants.HEAD);
      var jgitLog = jGit.log().add(head);
      paths.forEach(jgitLog::addPath);
      var spliterator = jgitLog.call().spliterator();
      return StreamSupport.stream(spliterator, false)
          .map(commit -> new GitChangeLog(commit, repository))
          .collect(Collectors.toList());
    });

    return result;
  }

  public Try<List<ChangeLog>> getChangeLogs(Collection<String> paths, int skip, int limit) {
    Try<List<ChangeLog>> result = Try.of(() -> {
      var repository = jGit.getRepository();
      var head = repository.resolve(Constants.HEAD);
      var jgitLog = jGit.log().add(head);
      paths.forEach(jgitLog::addPath);
      var spliterator = jgitLog.setSkip(skip).setMaxCount(limit).call().spliterator();
      return StreamSupport.stream(spliterator, false)
          .map(commit -> new GitChangeLog(commit, repository))
          .collect(Collectors.toList());
    });
    return result;
  }

  private String normalizePath(String path) {
    if (path == null) {
      return "";
    }
    return path.startsWith("/") ? path.substring(1) : path;
  }

  // 分支管理
  public Try<String> createBranch(String branchName) {
    return createBranch(branchName, null);
  }

  public Try<String> createBranch(String branchName, String fromRevision) {
    return Try.of(() -> {
      var createBranch = jGit.branchCreate()
          .setName(branchName);

      if (fromRevision != null) {
        createBranch.setStartPoint(fromRevision);
      }

      var ref = createBranch.call();
      return ref.getName();
    });
  }

  public Try<Boolean> deleteBranch(String branchName) {
    return Try.of(() -> {
      var result = jGit.branchDelete()
          .setBranchNames(branchName)
          .setForce(true)
          .call();
      return !result.isEmpty();
    });
  }

  public Try<Boolean> switchBranch(String branchName) {
    return Try.of(() -> {
      jGit.checkout()
          .setName(branchName)
          .setCreateBranch(false)
          .call();
      return true;
    });
  }

  public Try<List<String>> listBranches() {
    return Try.of(() -> {
      return jGit.branchList()
          .call()
          .stream()
          .map(ref -> ref.getName().replaceFirst("refs/heads/", ""))
          .collect(Collectors.toList());
    });
  }

  public Try<String> getCurrentBranch() {
    return Try.of(() -> {
      return jGit.getRepository().getBranch();
    });
  }

  public Try<Boolean> mergeBranch(String sourceBranch, String targetBranch, String message) {
    return Try.of(() -> {
      // 先切换到目标分支
      switchBranch(targetBranch).get();

      // 执行合并
      var sourceRef = jGit.getRepository().findRef(sourceBranch);
      var result = jGit.merge()
          .include(sourceRef)
          .setMessage(message)
          .call();

      return result.getMergeStatus().isSuccessful();
    });
  }

  // 标签管理
  public Try<String> createTag(String tagName, String message, String user, String email) {
    return createTag(tagName, null, message, user, email);
  }

  public Try<String> createTag(String tagName, String revision, String message, String user, String email) {
    return Try.of(() -> {
      var tagCommand = jGit.tag()
          .setName(tagName);

      if (StringUtils.isNotEmpty(email)) {
        tagCommand.setTagger(new PersonIdent(user, email))
            .setMessage(message);
      } else {
        tagCommand.setMessage(message + EMBED_USER_IN_MESSAGE_PREFIX + (StringUtils.isEmpty(user) ? SYSTEM_USERNAME : user));
      }
      if (revision != null) {
        var objectId = jGit.getRepository().resolve(revision);
        var revWalk = new RevWalk(jGit.getRepository());
        var commit = revWalk.parseCommit(objectId);
        tagCommand.setObjectId(commit);
        revWalk.close();
      }

      var ref = tagCommand.call();
      return ref.getName();
    });
  }

  public Try<Boolean> deleteTag(String tagName) {
    return Try.of(() -> {
      var result = jGit.tagDelete()
          .setTags(tagName)
          .call();
      return !result.isEmpty();
    });
  }

  public Try<List<Tag>> listTags() {
    return Try.of(() -> {
      var repository = jGit.getRepository();

      try (RevWalk revWalk = new RevWalk(repository)) {
        return jGit.tagList()
            .call()
            .stream()
            .map(ref -> getTag(revWalk, ref))
            .collect(Collectors.toList());
      }
    });
  }

  private Tag getTag(RevWalk revWalk, Ref ref) {
    try {
      RevTag tag = revWalk.parseTag(ref.getObjectId());
      return new GitTag(tag);
    } catch (Exception e) {
      // 轻量级tag返回空字符串
      try {
        var commit = revWalk.parseCommit(ref.getObjectId());
        var message = commit.getFullMessage();
        var author = commit.getAuthorIdent().getName();
        var time = LocalDateTime.ofInstant(commit.getAuthorIdent().getWhen().toInstant(),
            commit.getAuthorIdent().getTimeZone().toZoneId());
        return new GitTag(ref.getName().replaceFirst("refs/tags/", ""), message, author, time);
      } catch (Exception error) {
        LOGGER.warn("Failed to parse tag commit for {}: {}", ref.getName(), error.getMessage());
      }
    }
    return new GitTag(ref.getName().replaceFirst("refs/tags/", ""), "", "", null);
  }

  // 文件列表获取
  public Try<List<String>> getFileList() {
    return getFileList(Constants.HEAD);
  }

  public Try<List<String>> getFileList(String revision) {
    return Try.of(() -> {
      var objectId = jGit.getRepository().resolve(revision);
      if (objectId == null) {
        return Collections.emptyList();
      }

      var revWalk = new RevWalk(jGit.getRepository());
      var commit = revWalk.parseCommit(objectId);
      var tree = commit.getTree();

      var treeWalk = new TreeWalk(jGit.getRepository());
      treeWalk.addTree(tree);
      treeWalk.setRecursive(true);

      List<String> files = new ArrayList<>();
      while (treeWalk.next()) {
        files.add(treeWalk.getPathString());
      }
      treeWalk.close();
      revWalk.close();

      return files;
    });
  }

  public Try<List<String>> getFileListFromBranch(String branchName) {
    return Try.of(() -> {
      var branchRef = jGit.getRepository().findRef("refs/heads/" + branchName);
      if (branchRef == null) {
        throw new IllegalArgumentException("Branch not found: " + branchName);
      }
      return getFileList(branchRef.getObjectId().getName()).get();
    });
  }

  public Try<List<String>> getFileListFromTag(String tagName) {
    return Try.of(() -> {
      var tagRef = jGit.getRepository().findRef("refs/tags/" + tagName);
      if (tagRef == null) {
        throw new IllegalArgumentException("Tag not found: " + tagName);
      }
      return getFileList(tagRef.getObjectId().getName()).get();
    });
  }

  public Try<List<String>> getFileList(String path, String revision) {
    return Try.of(() -> {
      var objectId = jGit.getRepository().resolve(revision);
      if (objectId == null) {
        return Collections.emptyList();
      }

      var revWalk = new RevWalk(jGit.getRepository());
      var commit = revWalk.parseCommit(objectId);
      var tree = commit.getTree();

      var treeWalk = new TreeWalk(jGit.getRepository());
      treeWalk.addTree(tree);
      treeWalk.setRecursive(true);

      // 设置路径过滤器
      if (path != null && !path.isEmpty()) {
        treeWalk.setFilter(PathFilter.create(normalizePath(path)));
      }

      List<String> files = new ArrayList<>();
      while (treeWalk.next()) {
        files.add(treeWalk.getPathString());
      }
      treeWalk.close();
      revWalk.close();

      return files;
    });
  }

  // 历史文件内容访问
  public Try<String> getFileContent(String filePath, String revision) {
    return Try.of(() -> {
      var objectId = jGit.getRepository().resolve(revision);
      if (objectId == null) {
        throw new IllegalArgumentException("Revision not found: " + revision);
      }

      var revWalk = new RevWalk(jGit.getRepository());
      var commit = revWalk.parseCommit(objectId);
      var tree = commit.getTree();

      var treeWalk = TreeWalk.forPath(jGit.getRepository(), normalizePath(filePath), tree);
      if (treeWalk == null) {
        throw new IllegalArgumentException("File not found in revision: " + filePath);
      }

      var objectLoader = jGit.getRepository().open(treeWalk.getObjectId(0));
      var content = new String(objectLoader.getBytes(), StandardCharsets.UTF_8);

      treeWalk.close();
      revWalk.close();

      return content;
    });
  }

  public Try<byte[]> getFileBytes(String filePath, String revision) {
    return Try.of(() -> {
      var objectId = jGit.getRepository().resolve(revision);
      if (objectId == null) {
        throw new IllegalArgumentException("Revision not found: " + revision);
      }

      var revWalk = new RevWalk(jGit.getRepository());
      var commit = revWalk.parseCommit(objectId);
      var tree = commit.getTree();

      var treeWalk = TreeWalk.forPath(jGit.getRepository(), normalizePath(filePath), tree);
      if (treeWalk == null) {
        throw new IllegalArgumentException("File not found in revision: " + filePath);
      }

      var objectLoader = jGit.getRepository().open(treeWalk.getObjectId(0));
      var bytes = objectLoader.getBytes();

      treeWalk.close();
      revWalk.close();

      return bytes;
    });
  }

  public Try<String> getFileContentFromBranch(String filePath, String branchName) {
    return Try.of(() -> {
      var branchRef = jGit.getRepository().findRef("refs/heads/" + branchName);
      if (branchRef == null) {
        throw new IllegalArgumentException("Branch not found: " + branchName);
      }
      return getFileContent(filePath, branchRef.getObjectId().getName()).get();
    });
  }

  public Try<String> getFileContentFromTag(String filePath, String tagName) {
    return Try.of(() -> {
      var tagRef = jGit.getRepository().findRef("refs/tags/" + tagName);
      if (tagRef == null) {
        throw new IllegalArgumentException("Tag not found: " + tagName);
      }
      return getFileContent(filePath, tagRef.getObjectId().getName()).get();
    });
  }
}
