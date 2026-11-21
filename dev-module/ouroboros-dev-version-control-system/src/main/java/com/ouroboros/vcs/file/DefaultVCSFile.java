package com.ouroboros.vcs.file;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.ouroboros.vcs.VCSUserNameResolver;
import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.exception.FileLockedException;
import com.ouroboros.vcs.exception.FileNotExistException;
import com.ouroboros.vcs.exception.OperationNotAllowed;
import com.ouroboros.vcs.filesystem.FileSystemManager;
import com.ouroboros.vcs.lock.LockManager;
import com.ouroboros.vcs.lock.LockState;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.VersionState;
import com.ouroboros.vcs.versioning.VersioningManager;

public class DefaultVCSFile implements VCSFile {
  private final static Logger LOGGER = LoggerFactory.getLogger(DefaultVCSFile.class);
  private final VersioningManager versioningManager;
  private final FileSystemManager fileSystemManager;
  private final LockManager lockManager;
  private final VCSUserProvider vcsUserProvider;
  private final VCSUserNameResolver userNameResolver;
  private String oldRelativePath;
  private String relativePath;
  private BasicFileAttributes basicFileAttributes;
  private boolean isDeleted = false;
  private boolean isMoved = false;

  // === 新增：版本控制相关字段 ===
  private final String revision; // null表示当前工作区
  private final boolean isHistorical;

  // === 原有构造函数（兼容性） ===
  public DefaultVCSFile(VersioningManager versioningManager, FileSystemManager fileSystemManager, LockManager lockManager, VCSUserProvider vcsUserProvider, VCSUserNameResolver userNameResolver, String relativePath) {
    this(versioningManager, fileSystemManager, lockManager, vcsUserProvider, userNameResolver, relativePath, null);
  }

  // === 新增：支持版本的构造函数 ===
  public DefaultVCSFile(VersioningManager versioningManager,
                       FileSystemManager fileSystemManager,
                       LockManager lockManager,
                       VCSUserProvider vcsUserProvider,
                       VCSUserNameResolver userNameResolver,
                       String relativePath,
                       String revision) {
    this.versioningManager = versioningManager;
    this.fileSystemManager = fileSystemManager;
    this.lockManager = lockManager;
    this.vcsUserProvider = vcsUserProvider;
    this.userNameResolver = userNameResolver;
    this.relativePath = normalizePath(relativePath);
    this.revision = revision;
    this.isHistorical = revision != null;
  }

  private String normalizePath(String path) {
    if (path == null) {
      return "";
    } else if (path.startsWith("/") || path.startsWith("\\")) {
      return path.substring(1);
    } else {
      return path;
    }
  }


  @Override
  public boolean exists() {
    if (isHistorical) {
      // 检查历史版本中是否存在该文件
      return versioningManager.getFileList(revision)
          .map(files -> files.contains(relativePath))
          .getOrElse(false);
    }
    return fileSystemManager.exists(relativePath);
  }

  @Override
  public String getPath() {
    return relativePath;
  }

  @Override
  public String getFileName() {
    return Paths.get(relativePath).getFileName().toString();
  }

  @Override
  public String getContent() {
    if (isHistorical) {
      return versioningManager.getFileContent(relativePath, revision)
          .getOrElse("");
    }

    // 读取文件内容并转为字符串
    try (InputStream is = getInputStream()) {
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int length;
      while ((length = is.read(buffer)) != -1) {
        result.write(buffer, 0, length);
      }
      byte[] bytes = result.toByteArray();
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOGGER.error("Failed to read file content: {}", relativePath, e);
      return "";
    }
  }

  @Override
  public InputStream getInputStream() {
    if (isHistorical) {
      byte[] content = getBytesArray();
      return new ByteArrayInputStream(content);
    }

    File file = fileSystemManager.getFile(relativePath);
    if (file == null || !file.exists()) {
      LOGGER.error("File not found: {}", Paths.get(relativePath));
      // 返回一个空的输入流
      return new EmptyInputStream();
    }
    if (!file.canRead()) {
      LOGGER.error("File is not readable: {}", file.getAbsolutePath());
      // 返回一个空的输入流
      return new EmptyInputStream();
    }
    // 打开文件
    try {
      return new FileInputStream(file);
    } catch (Exception e) {
      LOGGER.error("Failed to open file: {}", file.getAbsolutePath(), e);
      return new EmptyInputStream();
    }
  }

  @Override
  public byte[] getBytesArray() {
    if (isHistorical) {
      return versioningManager.getFileBytes(relativePath, revision)
          .getOrElse(new byte[0]);
    }

    try (InputStream is = getInputStream()) {
      ByteArrayOutputStream result = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int length;
      while ((length = is.read(buffer)) != -1) {
        result.write(buffer, 0, length);
      }
      return result.toByteArray();
    } catch (IOException e) {
      LOGGER.error("Failed to read file content: {}", relativePath, e);
      return new byte[0];
    }
  }

  @Override
  public Long getSize() {
    if (isHistorical) {
      return (long) getBytesArray().length;
    }
    return fileSystemManager.getFile(relativePath).length();
  }

  @Override
  public LocalDateTime getCreationTime() {
    // 文件的创建时间不会随着revision变化，始终返回第一次提交的时间
    var state = versioningManager.getFileState(relativePath);
    if (state != VersionState.UNVERSIONED && state != VersionState.UNKNOWN) {
      var time = versioningManager.getFirstCommittedTime(relativePath);
      if (time != null && !LocalDateTime.MIN.equals(time)) {
        return time;
      }
    }
    // 从文件系统中获取文件的创建时间
    var creationTime = getBasicFileAttributes().creationTime();
    return LocalDateTime.ofInstant(creationTime.toInstant(), ZoneId.systemDefault());
  }

  @Override
  public LocalDateTime getLastCommittedTime() {
    if (isHistorical) {
      // 对于历史文件，使用revision对应的提交时间
      var revisions = getRevisions(0, 1);
      if (!revisions.isEmpty()) {
        return revisions.get(0).getCommitTime();
      }
      return null;
    }
    // 对于当前工作区文件，返回最后的提交时间
    var date = versioningManager.getLastCommittedTime(relativePath);
    if (LocalDateTime.MIN.equals(date)) {
      return null;
    }
    return date;
  }

  @Override
  public LocalDateTime getLastModifiedTime() {
    if (isHistorical) {
      // 对于历史文件，最后修改时间就是该revision的提交时间
      return getLastCommittedTime();
    }

    var state = versioningManager.getFileState(relativePath);
    if (state == VersionState.NOTCHANGED) {
      return versioningManager.getLastCommittedTime(relativePath);
    }
    // 从文件系统中获取文件的最后修改时间
    var lastModifiedTime = getBasicFileAttributes().lastModifiedTime();
    return LocalDateTime.ofInstant(lastModifiedTime.toInstant(), ZoneId.systemDefault());
  }

  @Override
  public VersionState getVersionState() {
    return versioningManager.getFileState(relativePath);
  }

  @Override
  public LockState getLockState() {
    var lockUser = lockManager.getLockOwner(relativePath);
    var currentUser = vcsUserProvider.getCurrentUser();
    if (StringUtils.isEmpty(lockUser)) {
      return LockState.UNLOCKED;
    }
    return lockUser.equals(currentUser)
        ? LockState.OWNED
        : LockState.LOCKED;
  }

  @Override
  public String getLockOwner() {
    return lockManager.getLockOwner(relativePath);
  }

  @Override
  public String getLockOwnerName() {
    var lockUser = lockManager.getLockOwner(relativePath);
    if (StringUtils.isEmpty(lockUser)) {
      return null;
    }
    return userNameResolver.resolve(lockUser);
  }



  private BasicFileAttributes getBasicFileAttributes() {
    if (basicFileAttributes == null) {
      var file = fileSystemManager.getFile(relativePath);
      try {
        basicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
      } catch (Exception e) {
        basicFileAttributes = new EmptyBasicFileAttributes();
      }
    }
    return basicFileAttributes;
  }

  // 一个空的输入流，用于返回文件不存在或无法读取的情况
  private static class EmptyInputStream extends InputStream {
    @Override
    public int read() {
      return -1;
    }
  }

  // 一个空的文件属性，用于返回文件不存在或无法读取的情况
  private static class EmptyBasicFileAttributes implements BasicFileAttributes {
    @Override
    public FileTime lastModifiedTime() {
      return FileTime.from(Instant.MIN);
    }

    @Override
    public FileTime lastAccessTime() {
      return FileTime.from(Instant.MIN);
    }

    @Override
    public FileTime creationTime() {
      return FileTime.from(Instant.MIN);
    }

    @Override
    public boolean isRegularFile() {
      return false;
    }

    @Override
    public boolean isDirectory() {
      return false;
    }

    @Override
    public boolean isSymbolicLink() {
      return false;
    }

    @Override
    public boolean isOther() {
      return false;
    }

    @Override
    public long size() {
      return 0;
    }

    @Override
    public Object fileKey() {
      return null;
    }
  }

  // === 新增方法实现 ===

  @Override
  public String getRevision() {
    return revision;
  }

  @Override
  public boolean isHistorical() {
    return isHistorical;
  }

  @Override
  public VCSFile atRevision(String revision) {
    return new DefaultVCSFile(versioningManager, fileSystemManager,
                             lockManager, vcsUserProvider, userNameResolver,
                             relativePath, revision);
  }

  @Override
  public VCSFile atBranch(String branchName) {
    return atRevision(branchName);
  }

  @Override
  public VCSFile atTag(String tagName) {
    return atRevision(tagName);
  }

  @Override
  public Try<FileDiff> diffWith(String otherRevision) {
    // 简单实现：基于文本内容比较
    return Try.of(() -> {
      String currentContent = isHistorical
          ? versioningManager.getFileContent(relativePath, revision).getOrElse("")
          : getContent();
      String otherContent = versioningManager.getFileContent(relativePath, otherRevision).getOrElse("");

      if (currentContent.equals(otherContent)) {
        return new FileDiff(revision, otherRevision, relativePath,
            FileDiff.DiffType.IDENTICAL, Collections.emptyList(), 0, 0);
      }

      // 简单的行差异计算
      String[] currentLines = currentContent.split("\n");
      String[] otherLines = otherContent.split("\n");

      // 这里做简化处理，实际应该使用更复杂的diff算法
      long added = Math.max(0, otherLines.length - currentLines.length);
      long deleted = Math.max(0, currentLines.length - otherLines.length);

      return new FileDiff(revision, otherRevision, relativePath,
          FileDiff.DiffType.MODIFIED, Collections.emptyList(), added, deleted);
    });
  }

  @Override
  public Try<FileDiff> diffWithWorkingCopy() {
    if (!isHistorical) {
      // 当前就是工作区文件
      return Try.of(() -> new FileDiff(revision, "HEAD", relativePath,
          FileDiff.DiffType.IDENTICAL, Collections.emptyList(), 0, 0));
    }
    return diffWith("HEAD");
  }

  @Override
  public List<FileRevision> getRevisions() {
    return getRevisions(0, Integer.MAX_VALUE);
  }

  @Override
  public List<FileRevision> getRevisions(int skip, int limit) {
    return versioningManager.getChangeLogs(relativePath, skip, limit)
        .stream()
        .map(this::toFileRevision)
        .collect(Collectors.toList());
  }

  private FileRevision toFileRevision(ChangeLog changeLog) {
    // 获取文件大小
    long fileSize = versioningManager.getFileBytes(relativePath, changeLog.getId())
        .map(bytes -> (long) bytes.length)
        .getOrElse(0L);

    return new FileRevision(
        changeLog.getId(),
        changeLog.getAuthor(),
        changeLog.getTime(),
        changeLog.getMessage(),
        fileSize,
        FileRevision.RevisionType.COMMIT
    );
  }

  // === 重写操作方法以支持只读检查 ===

  @Override
  public Try<Boolean> write(String content) {
    if (isHistorical) {
      return Try.failure(new OperationNotAllowed("Historical files are read-only"));
    }
    if (!isOperationAllowed(FileOperation.WRITE)) {
      return Try.failure(new OperationNotAllowed("Write operation not allowed"));
    }

    var inputStream = content == null
        ? new ByteArrayInputStream(new byte[0])
        : new ByteArrayInputStream(content.getBytes());
    return write(inputStream);
  }

  @Override
  public Try<Boolean> write(byte[] content) {
    if (isHistorical) {
      return Try.failure(new OperationNotAllowed("Historical files are read-only"));
    }
    if (!isOperationAllowed(FileOperation.WRITE)) {
      return Try.failure(new OperationNotAllowed("Write operation not allowed"));
    }
    return write(new ByteArrayInputStream(content));
  }

  @Override
  public Try<Boolean> write(InputStream content) {
    if (isHistorical) {
      return Try.failure(new OperationNotAllowed("Historical files are read-only"));
    }
    if (!isOperationAllowed(FileOperation.WRITE)) {
      return Try.failure(new OperationNotAllowed("Write operation not allowed"));
    }

    if (getLockState() == LockState.LOCKED) {
      return Try.failure(new FileLockedException(relativePath, lockManager.getLockOwner(relativePath)));
    }

    if (!exists()) {
      return fileSystemManager.createFile(relativePath, content).map(ignore -> true);
    }
    return fileSystemManager.write(relativePath, content)
        .peek(ignore -> versioningManager.notifyFileChange(relativePath))
        .map(ignore -> true);
  }

  @Override
  public Try<VCSFile> move(String newPath) {
    if (isHistorical) {
      return Try.failure(new OperationNotAllowed("Historical files are read-only"));
    }
    if (!isOperationAllowed(FileOperation.MOVE)) {
      return Try.failure(new OperationNotAllowed("Move operation not allowed"));
    }

    if (getLockState() == LockState.LOCKED) {
      return Try.failure(new FileLockedException(relativePath, lockManager.getLockOwner(relativePath)));
    }
    if (!exists()) {
      return Try.failure(new FileNotExistException(relativePath));
    }
    if (isMoved) {
      return Try.failure(new OperationNotAllowed("不可以再次移动"));
    }
    return fileSystemManager.move(relativePath, newPath)
        .peek(ignore -> {
          this.isMoved = true;
          this.oldRelativePath = relativePath;
          this.relativePath = newPath;
          versioningManager.notifyFileChange(relativePath, oldRelativePath);
        })
        .map(ignore -> this);
  }

  @Override
  public Try<VCSFile> remove() {
    if (isHistorical) {
      return Try.failure(new OperationNotAllowed("Historical files are read-only"));
    }
    if (!isOperationAllowed(FileOperation.DELETE)) {
      return Try.failure(new OperationNotAllowed("Delete operation not allowed"));
    }

    if (getLockState() == LockState.LOCKED) {
      return Try.failure(new FileLockedException(relativePath, lockManager.getLockOwner(relativePath)));
    }
    if (!exists() || isDeleted) {
      return Try.success(this);
    }
    return fileSystemManager.delete(relativePath)
        .peek(ignore -> {
          this.isDeleted = true;
          versioningManager.notifyFileChange(relativePath);
        })
        .map(ignore -> this);
  }

  @Override
  public Try<Boolean> commit(String message) {
    if (isHistorical) {
      return Try.failure(new OperationNotAllowed("Historical files cannot be committed"));
    }
    if (!isOperationAllowed(FileOperation.COMMIT)) {
      return Try.failure(new OperationNotAllowed("Commit operation not allowed"));
    }

    // 判断是否为删除、移动
    if (isDeleted) {
      return versioningManager.remove(relativePath, message);
    }
    if (isMoved) {
      return versioningManager.move(oldRelativePath, relativePath, message)
          .peek(ignore -> {
            this.isMoved = false;
            this.oldRelativePath = null;
          });
    }
    return versioningManager.commit(relativePath, message);
  }

  @Override
  public Try<Void> lock(boolean forceLock) {
    if (isHistorical) {
      return Try.failure(new OperationNotAllowed("Historical files cannot be locked"));
    }
    if (!isOperationAllowed(FileOperation.LOCK)) {
      return Try.failure(new OperationNotAllowed("Lock operation not allowed"));
    }

    var currentUser = vcsUserProvider.getCurrentUser();
    var lockUser = lockManager.getLockOwner(relativePath);

    // 未锁则锁
    if (StringUtils.isEmpty(lockUser)) {
      return lockManager
          .lock(relativePath, currentUser);
    }

    // 被本人锁则直接返回成功
    if (lockUser.equals(currentUser)) {
      return Try.success(null);
    }

    // 如果没有强制锁则失败
    if (!forceLock) {
      return Try.failure(new FileLockedException(relativePath, lockUser));
    }

    // 否则先解锁再锁
    return lockManager.unlock(relativePath)
        .flatMap(unused -> lockManager.lock(relativePath, currentUser));
  }

  @Override
  public Try<Void> unlock(boolean forceUnlock) {
    if (isHistorical) {
      return Try.failure(new OperationNotAllowed("Historical files cannot be unlocked"));
    }
    if (!isOperationAllowed(FileOperation.UNLOCK)) {
      return Try.failure(new OperationNotAllowed("Unlock operation not allowed"));
    }

    var currentUser = vcsUserProvider.getCurrentUser();
    var lockUser = lockManager.getLockOwner(relativePath);

    // 未锁则直接返回成功
    if (StringUtils.isEmpty(lockUser)) {
      return Try.success(null);
    }

    // 被本人锁则解锁
    if (lockUser.equals(currentUser)) {
      return lockManager.unlock(relativePath);
    }

    // 如果没有强制解锁则失败
    if (!forceUnlock) {
      return Try.failure(new FileLockedException(relativePath, lockUser));
    }

    return lockManager.unlock(relativePath);
  }

  @Override
  public Try<Boolean> rollback(boolean forceRollback) {
    if (isHistorical) {
      return Try.failure(new OperationNotAllowed("Historical files cannot be rolled back"));
    }
    if (!isOperationAllowed(FileOperation.ROLLBACK)) {
      return Try.failure(new OperationNotAllowed("Rollback operation not allowed"));
    }

    var lockState = getLockState();
    return switch (lockState) {
      case UNLOCKED, OWNED -> versioningManager.rollback(relativePath);
      default -> forceRollback
          ? versioningManager.rollback(relativePath)
          : Try.failure(new FileLockedException(relativePath, getLockOwner()));
    };
  }
}
