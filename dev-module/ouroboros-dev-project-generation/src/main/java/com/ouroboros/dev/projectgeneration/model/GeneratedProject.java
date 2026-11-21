package com.ouroboros.dev.projectgeneration.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public final class GeneratedProject implements AutoCloseable {

  private final String filename;
  private final long size;
  private final String sha256;
  private final Path path;
  private final InputStream content;
  private final Runnable onClose;
  private boolean closed;

  public GeneratedProject(String filename, long size, InputStream content) {
    this(filename, size, null, null, content, null);
  }

  public GeneratedProject(
      String filename,
      long size,
      String sha256,
      Path path,
      InputStream content,
      Runnable onClose) {
    if (filename == null || filename.isBlank() || size < 0 || content == null) {
      throw new IllegalArgumentException("Generated project file metadata is required");
    }
    this.filename = filename;
    this.size = size;
    this.sha256 = sha256;
    this.path = path;
    this.content = content;
    this.onClose = onClose;
  }

  public String filename() {
    return filename;
  }

  public long size() {
    return size;
  }

  public String sha256() {
    return sha256;
  }

  public Path path() {
    return path;
  }

  public InputStream content() {
    return content;
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    IOException failure = null;
    try {
      content.close();
    } catch (IOException e) {
      failure = e;
    }
    try {
      if (onClose != null) {
        onClose.run();
      }
    } catch (RuntimeException e) {
      if (failure != null) {
        e.addSuppressed(failure);
      }
      throw e;
    }
    if (failure != null) {
      throw failure;
    }
  }
}
