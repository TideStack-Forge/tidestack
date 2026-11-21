package com.ouroboros.vcs.lock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

import com.ouroboros.util.Json;

public class LocalFileLockManager implements LockManager {
  private final static Logger LOGGER = LoggerFactory.getLogger(LocalFileLockManager.class);
  final private String lockFilePath;
  private final ConcurrentMap<String, String> lockMap;

  public LocalFileLockManager(String lockFilePath) {
    this.lockFilePath = lockFilePath;
    this.lockMap = loadLockMap();
  }

  @Override
  public Try<Void> lock(String filePath, String user) {
    synchronized (this) {
      final String lockUser = lockMap.get(filePath);
      if (lockUser != null && !lockUser.equals(user)) {
        final String msg = "锁定失败，该文件已被 " + lockUser + " 锁定";
        return Try.failure(new IllegalStateException(msg));
      }
      if (lockUser == null) {
        lockMap.put(filePath, user);
        saveLockState();
      }
      return Try.success(null);
    }
  }

  @Override
  public Try<Void> lock(Collection<String> filePaths, String user) {
    synchronized (this) {
      for (String filePath : filePaths) {
        final String lockUser = lockMap.get(filePath);
        if (lockUser != null && !lockUser.equals(user)) {
          final String msg = "锁定失败，文件 " + filePath + " 已被 " + lockUser + " 锁定";
          return Try.failure(new IllegalStateException(msg));
        }
      }
      for (String filePath : filePaths) {
        lockMap.put(filePath, user);
      }
      saveLockState();
      return Try.success(null);
    }
  }

  @Override
  public Try<Void> unlock(String filePath) {
    synchronized (this) {
      final String lockUser = lockMap.remove(filePath);
      if (lockUser != null) {
        saveLockState();
      }
      return Try.success(null);
    }
  }

  @Override
  public Try<Void> unlock(Collection<String> filePaths) {
    synchronized (this) {
      filePaths.forEach(lockMap::remove);
      saveLockState();
      return Try.success(null);
    }
  }

  @Override
  public Try<Void> unlock(String filePath, String user) {
    synchronized (this) {
      final String lockUser = lockMap.get(filePath);
      if (lockUser == null) {
        return Try.success(null);
      }
      if (!lockUser.equals(user)) {
        var msg = "解锁失败，文件 " + filePath + " 已被 " + lockUser + " 锁定";
        return Try.failure(new IllegalStateException(msg));
      }
      lockMap.remove(filePath);
      saveLockState();
      return Try.success(null);
    }
  }

  @Override
  public Try<Void> unlock(Collection<String> filePaths, String user) {
    synchronized (this) {
      for (String filePath : filePaths) {
        final String lockUser = lockMap.get(filePath);
        if (lockUser == null) {
          continue;
        }
        if (!lockUser.equals(user)) {
          var msg = "解锁失败，文件 " + filePath + " 已被 " + lockUser + " 锁定";
          return Try.failure(new IllegalStateException(msg));
        }
      }
      for (String filePath : filePaths) {
        lockMap.remove(filePath);
      }
      saveLockState();
      return Try.success(null);
    }
  }

  @Override
  public Boolean isLocked(String filePath) {
    return lockMap.containsKey(filePath);
  }

  @Override
  public String getLockOwner(String filePath) {
    return lockMap.get(filePath);
  }

  private ConcurrentMap<String, String> loadLockMap() {
    final var lockFile = new File(lockFilePath);
    if (!lockFile.exists()) {
      return new ConcurrentSkipListMap<>();
    }
    try (var lines = Files.lines(Paths.get(lockFilePath))) {
      final var content = lines.collect(Collectors.joining("\n"));
      final Map<String, String> map = Json.toMap(content).entrySet().stream()
          .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue() == null ? null : e.getValue().toString()), Map::putAll);
      return new ConcurrentSkipListMap<>(map);
    } catch (IOException e) {
      final String msg = "Lockfile: " + lockFilePath + " failed to load !";
      LOGGER.info(msg, e);
      return new ConcurrentSkipListMap<>();
    }
  }

  private void saveLockState() {
    synchronized (this) {
      final String content = Json.toPrettyJsonString(lockMap);
      try (FileWriter fw = new FileWriter(lockFilePath); PrintWriter pw = new PrintWriter(fw)) {
        pw.print(content);
      } catch (IOException e) {
        final String msg = "Lockfile " + lockFilePath + " failed to save !";
        LOGGER.info(msg, e);
      }
    }
  }

  @Override
  public void destroy() {
    Try.run(() -> Files.deleteIfExists(Paths.get(lockFilePath)));
  }
}
