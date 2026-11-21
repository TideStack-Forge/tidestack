package com.ouroboros.vcs.filesystem;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.DigestUtils;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.vcs.exception.FileExistException;
import com.ouroboros.vcs.exception.FileNotExistException;
import com.ouroboros.vcs.exception.NotFileException;
import com.ouroboros.vcs.security.SecurePathValidator;

@SuppressWarnings({"unused"})
public class LocalFileSystemManager implements FileSystemManager {
  private final static Logger logger = LoggerFactory.getLogger(LocalFileSystemManager.class);

  private final String repositoryPath;
  private final SecurePathValidator pathValidator;

  public LocalFileSystemManager(String repositoryPath) {
    this.repositoryPath = repositoryPath;
    this.pathValidator = new SecurePathValidator(repositoryPath);
  }

  @Override
  public File getFile(String filePath) {
    Path safePath = pathValidator.validateAndResolvePath(filePath);
    return safePath.toFile();
  }

  @Override
  public boolean exists(String filePath) {
    try {
      return getFile(filePath).exists();
    } catch (SecurityException e) {
      logger.warn("Security violation in exists check: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public Try<String> getFileContent(String filePath) {
    return getExistFile(filePath)
        .mapTry(file -> {
          if (file.isDirectory()) {
            throw new NotFileException("该文件是个路径", filePath);
          }
          var lines = Files.readAllLines(file.toPath());
          return String.join("\n", lines);
        });
  }

  @Override
  public Try<byte[]> getFileBytes(String filePath) {
    return getExistFile(filePath)
        .mapTry(file -> {
          if (file.isDirectory()) {
            throw new NotFileException("该文件是个路径", filePath);
          }
          return Files.readAllBytes(file.toPath());
        });
  }

  @Override
  public Try<InputStream> getFileInputStream(String filePath) {
    return getExistFile(filePath)
        .mapTry(file -> {
          if (file.isDirectory()) {
            throw new PlatformException("该文件是个路径");
          }
          return Files.newInputStream(file.toPath());
        });
  }

  @Override
  public Try<Void> createFile(String filePath, String content) {
    var inputStream = content == null
        ? new ByteArrayInputStream(new byte[0])
        : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    return createFile(filePath, inputStream);
  }

  @Override
  public Try<Void> createFile(String filePath, byte[] content) {
    return createFile(filePath, new ByteArrayInputStream(content));
  }

  @Override
  public Try<Void> createFile(String filePath, InputStream content) {
    var createResult = Try.of(() -> {
      var file = getFile(filePath);
      if (file.exists()) {
        throw new BusinessException("不能创建文件，因为文件已存在");
      }
      var dir = file.getParent();
      if (!(new File(dir)).exists()) {
        Files.createDirectories(Paths.get(dir));
      }
      return file;
    });

    var writeResult = createResult
        .mapTry(file -> Files.copy(content, file.toPath()))
        .andFinally(() -> Try.run(content::close));

    return writeResult.map(ignore -> null);
  }

  @Override
  public Try<Void> write(String filePath, String content) {
    return write(filePath, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public Try<Void> write(String filePath, byte[] content) {
    return write(filePath, new ByteArrayInputStream(content));
  }

  @Override
  public Try<Void> write(String filePath, InputStream content) {
    var writeResult = getExistFile(filePath)
        .mapTry(file -> {
          if (file.isDirectory()) {
            throw new PlatformException("该文件是个路径");
          }
          // 若内容没有改变则跳过写入
          var oldContentHash = getFileInputStream(filePath).mapTry(DigestUtils::md5Digest).getOrElseThrow(e -> e);
          // TODO: 待优化，直接读取全部内容可能会导致内存溢出

          var newContent = Try.of(content::readAllBytes).getOrElseThrow(e -> e);
          var newContentHash = DigestUtils.md5Digest(newContent);
          if (Arrays.equals(oldContentHash, newContentHash)) {
            return false;
          }
          Files.write(file.toPath(), newContent);
          return true;
        });
    return writeResult.map(ignore -> null);
  }

  @Override
  public Try<Void> delete(String filePath) {
    var deleteResult = Try.run(() -> {
      var file = getFile(filePath);
      if (!file.exists()) {
        return;
      }
      if (file.isDirectory()) {
        throw new PlatformException("不能删除文件，因为路径是一个目录");
      }
      Files.delete(file.toPath());
    });
    return deleteResult;
  }

  @Override
  public Try<Void> move(String sourcePath, String targetPath) {
    var ensureSource = Try.of(() -> {
      var file = getFile(sourcePath);
      if (!file.exists()) {
        throw new FileNotExistException(sourcePath);
      }
      if (file.isDirectory()) {
        throw new NotFileException("不能移动文件，因为源文件是一个路径", sourcePath);
      }
      return file;
    });

    var moveResult = ensureSource.mapTry(file -> {
      var newFile = getFile(targetPath);
      if (newFile.exists()) {
        throw new FileExistException(targetPath);
      }
      var dir = newFile.getParent();
      if (!(new File(dir)).exists()) {
        Files.createDirectories(Paths.get(dir));
      }
      Files.move(file.toPath(), newFile.toPath());
      return null;
    });

    return moveResult.map(ignore -> null);
  }

  @Override
  public Try<Void> copy(String sourcePath, String targetPath) {
    var source = getFile(sourcePath);
    var target = getFile(targetPath);
    if (!source.exists()) {
      return Try.failure(new FileNotExistException("源文件不存在", sourcePath));
    }
    if (target.exists()) {
      return Try.failure(new FileExistException("目标文件已存在", targetPath));
    }
    return Try.run(() -> Files.copy(source.toPath(), target.toPath()));
  }

  @Override
  public FileWalker walk(String directoryPath) {
    return new FileWalkerImpl(directoryPath);
  }

  @Override
  public Try<Void> createDirectory(String filePath) {
    return Try.run(() -> Files.createDirectories(Paths.get(repositoryPath, filePath)));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public Try<Void> deleteDirectory(String path) {
    var deleteResult = Try.run(() -> {
      var file = getFile(path);
      if (!file.exists()) {
        return;
      }
      if (file.isFile()) {
        throw new PlatformException("不能删除目录，因为该路径是个文件");
      }
      try (var stream = Files.walk(file.toPath())) {
        stream.sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
      }
    });
    return deleteResult;
  }

  @Override
  public void destroy() {

  }

  private Try<File> getExistFile(String filePath) {
    var file = getFile(filePath);
    if (!file.exists()) {
      return Try.failure(new FileNotExistException(filePath));
    }
    return Try.success(file);
  }

  class FileWalkerImpl implements FileWalker {

    private final List<String> directories = new ArrayList<>();
    private final List<FileFilter> filters = new ArrayList<>();
    private int depth = Integer.MAX_VALUE;

    public FileWalkerImpl(String directoryPath) {
      directories.add(directoryPath);
    }

    @Override
    public FileWalker directory(String directoryPath) {
      directories.add(directoryPath);
      return this;
    }

    @Override
    public FileWalker depth(int depth) {
      this.depth = depth;
      return this;
    }

    @Override
    public FileWalker filter(FileFilter filter) {
      filters.add(filter);
      return this;
    }

    @Override
    public FileWalker filter(Pattern pattern) {
      filters.add(file -> pattern.matcher(file.getName()).matches());
      return this;
    }

    @Override
    public FileWalker filter(String extFileName) {
      if (StringUtils.isNotEmpty(extFileName)) {
        filters.add(file -> file.getName().endsWith(extFileName));
      }
      return this;
    }

    @Override
    public Stream<File> stream() {
      var iterator = deepFirstIterator();
      var spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL);
      var rawStream = StreamSupport.stream(spliterator, false);
      Predicate<File> filter = file -> filters.stream().allMatch(f -> f.accept(file));
      return rawStream.filter(filter);
    }

    private Iterator<File> deepFirstIterator() {
      // 生成迭代器，要逐个遍历 directories 中的目录
      return new Iterator<>() {
        private final Deque<File> stack = new ArrayDeque<>();
        private int currentDepth = 0;
        private int currentDirectoryIndex = 0;

        @Override
        public boolean hasNext() {
          return currentDirectoryIndex < directories.size() || !stack.isEmpty();
        }

        @Override
        public File next() {
          if (currentDirectoryIndex < directories.size()) {
            var directory = getFile(directories.get(currentDirectoryIndex));
            currentDirectoryIndex++;
            stack.push(directory);
            return directory;
          }
          if (stack.isEmpty()) {
            throw new NoSuchElementException();
          }
          var file = stack.pop();
          if (file.isDirectory() && currentDepth < depth) {
            currentDepth++;
            var children = file.listFiles();
            if (children != null) {
              for (var child : children) {
                if (child.getName().startsWith(".")) {
                  continue;
                }
                stack.push(child);
              }
            }
          }
          return file;
        }
      };
    }
  }
}
