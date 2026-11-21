package com.ouroboros.vcs.action;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;

import com.ouroboros.vcs.VCSUserProvider;
import com.ouroboros.vcs.exception.MultipleFilesLockedException;
import com.ouroboros.vcs.exception.VCSException;
import com.ouroboros.vcs.filesystem.FileSystemManager;
import com.ouroboros.vcs.lock.LockManager;
import com.ouroboros.vcs.versioning.VersioningManager;

public class DefaultBatchAction extends AbstractVCSAction<VCSAction.BatchAction> implements VCSAction.BatchAction {
  private final VersioningManager versioningManager;
  private final FileSystemManager fileSystemManager;
  private final LockManager lockManager;

  private final Set<String> needAddPaths = new HashSet<>();
  private final Set<String> needRemovePaths = new HashSet<>();
  private final Set<String> needMovePaths = new HashSet<>();
  private final List<Supplier<Try<Void>>> contentWriters = new ArrayList<>();
  private final Set<String> writtenPaths = new LinkedHashSet<>();
  private Map<String, Optional<byte[]>> originals;
  private Throwable commitFailure;
  private final Set<String> autoAcquiredPaths = new LinkedHashSet<>();
  private final List<Runnable> writeValidations = new ArrayList<>();

  public DefaultBatchAction(VersioningManager versioningManager, FileSystemManager fileSystemManager, LockManager lockManager, VCSUserProvider vcsUserProvider) {
    super(versioningManager, vcsUserProvider);
    this.versioningManager = versioningManager;
    this.fileSystemManager = fileSystemManager;
    this.lockManager = lockManager;
  }

  @Override
  protected boolean doWriteContent() {
    writeValidations.forEach(Runnable::run);
    originals = new LinkedHashMap<>();
    writtenPaths.forEach(path -> originals.put(path, fileSystemManager.exists(path)
        ? Optional.of(fileSystemManager.getFileBytes(path).get()) : Optional.empty()));
    return Try.of(() -> {
      contentWriters.forEach(writer -> writer.get().get());
      versioningManager.notifyFileChange(needAddPaths).get();
      versioningManager.notifyFileChange(needRemovePaths).get();
      versioningManager.notifyFileChange(needMovePaths).get();
      return true;
    }).onFailure(failure -> {
      restoreOriginals(originals, failure);
      originals = null;
    }).get();
  }

  @Override
  public BatchAction beforeWrite(Runnable validation) {
    writeValidations.add(java.util.Objects.requireNonNull(validation));
    return this;
  }

  @Override
  public Try<Boolean> write(boolean forceWrite) {
    return commitFailure == null ? super.write(forceWrite) : Try.failure(commitFailure);
  }

  @Override
  public Try<Boolean> commit(String message) {
    if (commitFailure != null) { return Try.failure(commitFailure); }
    var result = Try.of(() -> super.commit(message)).flatMap(value -> value).onFailure(failure -> {
      commitFailure = failure;
      if (!committed && originals != null) { restoreOriginals(originals, failure); }
      if (!committed && !autoAcquiredPaths.isEmpty()) {
        lockManager.unlock(autoAcquiredPaths, commitUser == null ? vcsUserProvider.getCurrentUser() : commitUser)
            .onFailure(failure::addSuppressed);
      }
    });
    originals = null;
    return result;
  }

  private void restoreOriginals(Map<String, Optional<byte[]>> originals, Throwable failure) {
    var paths = new ArrayList<>(originals.keySet());
    Collections.reverse(paths);
    paths.forEach(path -> Try.run(() -> {
      var content = originals.get(path);
      if (content.isPresent()) {
        if (fileSystemManager.exists(path)) {
          fileSystemManager.write(path, content.get()).get();
        } else {
          fileSystemManager.createFile(path, content.get()).get();
        }
      } else {
        fileSystemManager.delete(path).get();
      }
    }).onFailure(cause -> failure.addSuppressed(new VCSException("恢复批量写入路径失败: " + path, cause))));
    versioningManager.notifyFileChange(paths).onFailure(failure::addSuppressed);
  }

  @Override
  public BatchAction add(String relativePath) {
    needAddPaths.add(relativePath);
    needRemovePaths.remove(relativePath);
    return this;
  }

  @Override
  public BatchAction add(Collection<String> relativePaths) {
    needAddPaths.addAll(relativePaths);
    needRemovePaths.removeAll(relativePaths);
    return this;
  }

  @Override
  public BatchAction create(String relativePath, String content) {
    var inputStream = StringUtils.isEmpty(content)
        ? new ByteArrayInputStream(new byte[0])
        : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    return create(relativePath, inputStream);
  }

  @Override
  public BatchAction create(String relativePath, byte[] content) {
    return create(relativePath, new ByteArrayInputStream(content));
  }

  @Override
  public BatchAction create(String relativePath, InputStream content) {
    needRemovePaths.remove(relativePath);
    needAddPaths.add(relativePath);
    writtenPaths.add(relativePath);
    contentWriters.add(() -> fileSystemManager.createFile(relativePath, content));
    return this;
  }

  @Override
  public BatchAction remove(String relativePath) {
    needAddPaths.remove(relativePath);
    needRemovePaths.add(relativePath);
    writtenPaths.add(relativePath);
    contentWriters.add(() -> fileSystemManager.delete(relativePath));
    return this;
  }

  @Override
  public BatchAction remove(Collection<String> relativePaths) {
    needAddPaths.removeAll(relativePaths);
    needRemovePaths.addAll(relativePaths);
    var removals = List.copyOf(relativePaths);
    writtenPaths.addAll(removals);
    contentWriters.add(() -> Try.run(() -> removals.forEach(path -> fileSystemManager.delete(path).get())));
    return this;
  }

  @Override
  public BatchAction update(String relativePath, String content) {
    var inputStream = StringUtils.isEmpty(content)
        ? new ByteArrayInputStream(new byte[0])
        : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    return this.update(relativePath, inputStream);
  }

  @Override
  public BatchAction update(String relativePath, byte[] content) {
    return this.update(relativePath, new ByteArrayInputStream(content));
  }

  @Override
  public BatchAction update(String relativePath, InputStream content) {
    needRemovePaths.remove(relativePath);
    needAddPaths.add(relativePath);
    writtenPaths.add(relativePath);
    contentWriters.add(() -> fileSystemManager.write(relativePath, content));
    return this;
  }

  @Override
  public BatchAction move(String relativePath, String newRelativePath) {
    return move(relativePath, newRelativePath, null);
  }

  @Override
  public BatchAction move(String relativePath, String newRelativePath, Function<String, String> updateContent) {
    needMovePaths.add(relativePath);
    needRemovePaths.add(relativePath);
    needAddPaths.add(newRelativePath);
    writtenPaths.add(relativePath);
    writtenPaths.add(newRelativePath);
    contentWriters.add(() -> fileSystemManager.move(relativePath, newRelativePath).flatMap(ignore ->
        updateContent == null ? Try.success(null) : fileSystemManager.getFileContent(newRelativePath)
            .mapTry(updateContent::apply).flatMap(content -> fileSystemManager.write(newRelativePath, content))));
    return this;
  }

  @Override
  protected void doPrepareCommitContent(VersioningManager.OperationChain chain) {
    needAddPaths.forEach(chain::add);
    needRemovePaths.forEach(chain::remove);
    needMovePaths.forEach(chain::remove);
  }

  @Override
  protected Try<Void> doCheckLock(String user) {
    var allLockedFileAndOwners = Stream.concat(needAddPaths.stream(), needRemovePaths.stream())
        .map(file -> Tuple.of(file, lockManager.getLockOwner(file)))
        .filter(t -> StringUtils.isNotBlank(t._2()))
        .collect(Collectors.toSet());
    if (allLockedFileAndOwners.isEmpty()) {
      return Try.success(null);
    }

    // 检查是否所有文件都被当前用户锁定
    var checkLockResult = allLockedFileAndOwners.stream()
        .map(Tuple2::_2)
        .distinct()
        .allMatch(owner -> owner.equals(user));
    if (checkLockResult) {
      return Try.success(null);
    }

    // 返回失败结果
    var lockedFileOwnersMap = allLockedFileAndOwners.stream()
        .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2, (v1, v2) -> v2));
    return Try.failure(new MultipleFilesLockedException(lockedFileOwnersMap));
  }

  @Override
  protected Try<Void> doLock(String user) {
    var allLockedFileAndOwners = Stream.concat(needAddPaths.stream(), needRemovePaths.stream())
        .map(file -> {
          String previous = lockManager.getLockOwner(file);
          var result = lockManager.lock(file, user);
          if (result.isSuccess() && StringUtils.isBlank(previous)) { autoAcquiredPaths.add(file); }
          return Tuple.of(file, result);
        })
        .filter(t -> t._2().isFailure())
        .collect(Collectors.toSet());
    if (allLockedFileAndOwners.isEmpty()) {
      return Try.success(null);
    }

    var lockedFileOwnersMap = allLockedFileAndOwners.stream()
        .map(t -> t.map2(ignore -> lockManager.getLockOwner(t._1)))
        .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2, (v1, v2) -> v2));
    return Try.failure(new MultipleFilesLockedException(lockedFileOwnersMap));
  }

  @Override
  protected void doUnlock(String user) {
    Stream.concat(needAddPaths.stream(), needRemovePaths.stream())
        .forEach(file -> lockManager.unlock(file, user));
  }
}
