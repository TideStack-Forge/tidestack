package com.ouroboros.vcs.lock;

import java.util.Collection;

import io.vavr.control.Try;

@SuppressWarnings("unused")
public interface LockManager {
  Try<Void> lock(String filePath, String user);

  Try<Void> lock(Collection<String> filePaths, String user);

  Try<Void> unlock(String filePath);

  Try<Void> unlock(Collection<String> filePaths);

  Try<Void> unlock(String filePath, String user);

  Try<Void> unlock(Collection<String> filePaths, String user);

  void destroy();

  Boolean isLocked(String relativePath);

  String getLockOwner(String relativePath);
}
