package com.ouroboros.vcs.filesystem;

import java.io.File;
import java.io.FileFilter;
import java.io.InputStream;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.vavr.control.Try;

public interface FileSystemManager {
  void destroy();

  File getFile(String path);

  boolean exists(String path);

  Try<Void> createFile(String path, InputStream content);

  Try<Void> createFile(String path, String content);

  Try<Void> createFile(String path, byte[] content);

  Try<String> getFileContent(String path);

  Try<byte[]> getFileBytes(String path);

  Try<InputStream> getFileInputStream(String path);

  Try<Void> write(String path, String content);

  Try<Void> write(String path, byte[] content);

  Try<Void> write(String path, InputStream content);

  Try<Void> delete(String path);

  Try<Void> move(String sourcePath, String targetPath);

  Try<Void> copy(String sourcePath, String targetPath);

  Try<Void> createDirectory(String path);

  FileWalker walk(String directoryPath);

  interface FileWalker {
    FileWalker directory(String directoryPath);

    FileWalker depth(int depth);

    FileWalker filter(FileFilter filter);

    FileWalker filter(Pattern pattern);

    FileWalker filter(String extFileName);

    Stream<File> stream();
  }
}
