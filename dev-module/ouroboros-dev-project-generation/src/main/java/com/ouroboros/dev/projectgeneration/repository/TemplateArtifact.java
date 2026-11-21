package com.ouroboros.dev.projectgeneration.repository;

import java.nio.file.Path;

public final class TemplateArtifact implements AutoCloseable {

  private final String sha256;
  private final Path path;
  private final long size;
  private final Runnable onClose;
  private boolean closed;

  TemplateArtifact(String sha256, Path path, long size, Runnable onClose) {
    this.sha256 = sha256;
    this.path = path;
    this.size = size;
    this.onClose = onClose;
  }

  public String sha256() {
    return sha256;
  }

  public Path path() {
    return path;
  }

  public long size() {
    return size;
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    if (onClose != null) {
      onClose.run();
    }
  }
}
