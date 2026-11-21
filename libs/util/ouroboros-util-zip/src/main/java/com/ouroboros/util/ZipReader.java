package com.ouroboros.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;

/**
 * ZipReader类提供ZIP文件读取功能，包括获取文件列表、文件信息、
 * 检查文件存在性、读取文件内容、解压文件等功能。
 * 该类实现了安全检查机制，防止路径遍历攻击和资源耗尽攻击。
 */
@SuppressWarnings("unused")
public class ZipReader {
  private static final Logger logger = LoggerFactory.getLogger(ZipReader.class);
  private static final int DEFAULT_BUFFER_SIZE = 8192;
  // 100MB
  private static final long MAX_ENTRY_SIZE = 100L * 1024 * 1024;
  private static final int MAX_ENTRIES = 10000;

  // ==================== 获取文件列表 ====================

  /**
   * 通过文件路径获取ZIP文件中的文件列表
   * @param zipFilePath ZIP文件的路径
   * @return 文件名列表
   */
  public List<String> getFileList(String zipFilePath) {
    return getFileList(new File(zipFilePath));
  }

  /**
   * 通过File对象获取ZIP文件中的文件列表
   * @param zipFile ZIP文件对象
   * @return 文件名列表
   */
  public List<String> getFileList(File zipFile) {
    if (!zipFile.exists() || !zipFile.isFile()) {
      logger.warn("Zip file does not exist: {}", zipFile.getAbsolutePath());
      return Collections.emptyList();
    }

    try (var zip = new ZipFile(zipFile)) {
      return zip.stream()
          .map(ZipEntry::getName)
          .collect(Collectors.toList());
    } catch (IOException e) {
      logger.error("Failed to read zip file: {}", zipFile.getAbsolutePath(), e);
      return Collections.emptyList();
    }
  }

  /**
   * 通过输入流获取ZIP文件中的所有文件列表
   * @param zipStream ZIP文件的输入流
   * @return 文件名列表
   */
  public List<String> getFileList(InputStream zipStream) {
    return getFileList(zipStream, entry -> true);
  }

  /**
   * 通过输入流获取ZIP文件中符合条件的文件列表
   * @param zipStream ZIP文件的输入流
   * @param filter 条件过滤器
   * @return 符合条件的文件名列表
   */
  public List<String> getFileList(InputStream zipStream, Predicate<ZipEntry> filter) {
    try (var zip = new ZipInputStream(zipStream)) {
      List<String> list = new ArrayList<>();
      ZipEntry zipEntry;
      int entryCount = 0;

      while ((zipEntry = zip.getNextEntry()) != null) {
        if (++entryCount > MAX_ENTRIES) {
          throw new IOException("Too many entries in zip file (max: " + MAX_ENTRIES + ")");
        }

        if (filter.test(zipEntry)) {
          list.add(zipEntry.getName());
        }
        zip.closeEntry();
      }
      return list;
    } catch (IOException e) {
      logger.error("Failed to read zip file from InputStream", e);
      return Collections.emptyList();
    }
  }

  // ==================== 获取文件信息 ====================

  /**
   * 通过文件路径获取ZIP文件中所有文件的详细信息
   * @param zipFilePath ZIP文件的路径
   * @return 文件信息列表
   */
  public List<ZipEntryInfo> getFileInfoList(String zipFilePath) {
    return getFileInfoList(new File(zipFilePath));
  }

  /**
   * 通过File对象获取ZIP文件中所有文件的详细信息
   * @param zipFile ZIP文件对象
   * @return 文件信息列表
   */
  public List<ZipEntryInfo> getFileInfoList(File zipFile) {
    if (!zipFile.exists() || !zipFile.isFile()) {
      return Collections.emptyList();
    }

    try (var zip = new ZipFile(zipFile)) {
      return zip.stream()
          .map(entry -> new ZipEntryInfo(
              entry.getName(),
              entry.getSize(),
              entry.getCompressedSize(),
              entry.isDirectory(),
              entry.getTime(),
              entry.getCrc()
          ))
          .collect(Collectors.toList());
    } catch (IOException e) {
      logger.error("Failed to read zip file info: {}", zipFile.getAbsolutePath(), e);
      return Collections.emptyList();
    }
  }

  // ==================== 文件存在性检查 ====================

  /**
   * 检查ZIP文件中是否包含指定文件
   * @param zipFilePath ZIP文件的路径
   * @param fileName 要检查的文件名
   * @return 是否包含该文件
   */
  public boolean containsFile(String zipFilePath, String fileName) {
    return containsFile(new File(zipFilePath), fileName);
  }

  /**
   * 检查ZIP文件中是否包含指定文件
   * @param zipFile ZIP文件对象
   * @param fileName 要检查的文件名
   * @return 是否包含该文件
   */
  public boolean containsFile(File zipFile, String fileName) {
    if (!zipFile.exists() || !zipFile.isFile()) {
      return false;
    }

    try (var zip = new ZipFile(zipFile)) {
      return zip.getEntry(fileName) != null;
    } catch (IOException e) {
      logger.error("Failed to check file existence in zip: {}", zipFile.getAbsolutePath(), e);
      return false;
    }
  }

  /**
   * 检查ZIP输入流中是否包含指定文件
   * @param zipStream ZIP文件的输入流
   * @param fileName 要检查的文件名
   * @return 是否包含该文件
   */
  public boolean containsFile(InputStream zipStream, String fileName) {
    try (var zip = new ZipInputStream(zipStream)) {
      ZipEntry zipEntry;
      while ((zipEntry = zip.getNextEntry()) != null) {
        if (zipEntry.getName().equals(fileName)) {
          return true;
        }
        zip.closeEntry();
      }
      return false;
    } catch (IOException e) {
      logger.error("Failed to check file existence in zip InputStream", e);
      return false;
    }
  }

  // ==================== 读取文件内容 ====================

  /**
   * 查看ZIP文件中指定文件的内容
   * @param zipFilePath ZIP文件的路径
   * @param fileName 要查看的文件名
   * @return 文件内容的字节数组
   */
  public byte[] peekFileContent(String zipFilePath, String fileName) {
    return peekFileContent(new File(zipFilePath), fileName);
  }

  /**
   * 查看ZIP文件中指定文件的内容
   * @param zipFile ZIP文件对象
   * @param fileName 要查看的文件名
   * @return 文件内容的字节数组
   */
  public byte[] peekFileContent(File zipFile, String fileName) {
    if (!zipFile.exists() || !zipFile.isFile()) {
      logger.warn("Zip file does not exist: {}", zipFile.getAbsolutePath());
      return new byte[0];
    }

    try (var zip = new ZipFile(zipFile)) {
      var entry = zip.getEntry(fileName);
      if (entry == null || entry.isDirectory()) {
        return new byte[0];
      }

      // 安全检查
      if (entry.getSize() > MAX_ENTRY_SIZE) {
        logger.warn("Entry too large: {} ({} bytes)", fileName, entry.getSize());
        return new byte[0];
      }

      try (var entryInputStream = zip.getInputStream(entry)) {
        return IOUtils.toByteArray(entryInputStream);
      }
    } catch (IOException e) {
      logger.error("Failed to read file content: {} from {}", fileName, zipFile.getAbsolutePath(), e);
      return new byte[0];
    }
  }

  /**
   * 查看ZIP输入流中指定文件的内容
   * @param zipStream ZIP文件的输入流
   * @param fileName 要查看的文件名
   * @return 文件内容的字节数组
   */
  public byte[] peekFileContent(InputStream zipStream, String fileName) {
    try (var zip = new ZipInputStream(zipStream)) {
      ZipEntry zipEntry;
      int entryCount = 0;

      while ((zipEntry = zip.getNextEntry()) != null) {
        if (++entryCount > MAX_ENTRIES) {
          throw new IOException("Too many entries in zip file");
        }

        if (zipEntry.getName().equals(fileName) && !zipEntry.isDirectory()) {
          // 使用限制读取的包装器
          var limitedStream = new LimitedZipEntryInputStream(zip, zipEntry.getSize());
          return IOUtils.toByteArray(limitedStream);
        }
        zip.closeEntry();
      }
      return new byte[0];
    } catch (IOException e) {
      logger.error("Failed to read file content: {} from InputStream", fileName, e);
      return new byte[0];
    }
  }

  // ==================== 读取文件内容为字符串 ====================

  /**
   * 查看ZIP文件中指定文件的内容并转换为字符串
   * @param zipFilePath ZIP文件的路径
   * @param fileName 要查看的文件名
   * @param charset 字符编码
   * @return 文件内容的字符串
   */
  public String peekFileContentAsString(String zipFilePath, String fileName, String charset) {
    return peekFileContentAsString(new File(zipFilePath), fileName, charset);
  }

  /**
   * 查看ZIP文件中指定文件的内容并转换为字符串（使用UTF-8编码）
   * @param zipFilePath ZIP文件的路径
   * @param fileName 要查看的文件名
   * @return 文件内容的字符串
   */
  public String peekFileContentAsString(String zipFilePath, String fileName) {
    return peekFileContentAsString(zipFilePath, fileName, StandardCharsets.UTF_8.name());
  }

  /**
   * 查看ZIP文件中指定文件的内容并转换为字符串
   * @param zipFile ZIP文件对象
   * @param fileName 要查看的文件名
   * @param charset 字符编码
   * @return 文件内容的字符串
   */
  public String peekFileContentAsString(File zipFile, String fileName, String charset) {
    var buffer = peekFileContent(zipFile, fileName);
    if (buffer.length == 0) {
      return "";
    }
    return Try.of(() -> new String(buffer, charset))
        .onFailure(e -> logger.error("Failed to convert bytes to string with charset: {}", charset, e))
        .getOrElse("");
  }

  /**
   * 查看ZIP文件中指定文件的内容并转换为字符串（使用UTF-8编码）
   * @param zipFile ZIP文件对象
   * @param fileName 要查看的文件名
   * @return 文件内容的字符串
   */
  public String peekFileContentAsString(File zipFile, String fileName) {
    return peekFileContentAsString(zipFile, fileName, StandardCharsets.UTF_8.name());
  }

  /**
   * 查看ZIP输入流中指定文件的内容并转换为字符串
   * @param zipStream ZIP文件的输入流
   * @param fileName 要查看的文件名
   * @param charset 字符编码
   * @return 文件内容的字符串
   */
  public String peekFileContentAsString(InputStream zipStream, String fileName, String charset) {
    var buffer = peekFileContent(zipStream, fileName);
    if (buffer.length == 0) {
      return "";
    }
    return Try.of(() -> new String(buffer, charset))
        .onFailure(e -> logger.error("Failed to convert bytes to string with charset: {}", charset, e))
        .getOrElse("");
  }

  /**
   * 查看ZIP输入流中指定文件的内容并转换为字符串（使用UTF-8编码）
   * @param zipStream ZIP文件的输入流
   * @param fileName 要查看的文件名
   * @return 文件内容的字符串
   */
  public String peekFileContentAsString(InputStream zipStream, String fileName) {
    return peekFileContentAsString(zipStream, fileName, StandardCharsets.UTF_8.name());
  }

  // ==================== 解压文件 ====================

  /**
   * 解压ZIP文件中的指定文件到输出路径
   * @param zipFilePath ZIP文件的路径
   * @param outputFilePath 输出文件路径
   * @return 操作结果
   */
  public Try<Void> extractFile(String zipFilePath, String outputFilePath) {
    return extractFile(new File(zipFilePath), outputFilePath);
  }

  /**
   * 解压ZIP文件中的指定文件到输出路径
   * @param zipFile ZIP文件对象
   * @param outputFilePath 输出文件路径
   * @return 操作结果
   */
  public Try<Void> extractFile(File zipFile, String outputFilePath) {
    return extractFile(zipFile, outputFilePath, (wrapper, outputStream) -> {
      try {
        wrapper.copyTo(outputStream);
        return true;
      } catch (IOException e) {
        logger.error("Failed to copy file: {}", wrapper.getName(), e);
        return false;
      }
    });
  }

  /**
   * 解压ZIP文件中的指定文件到输出路径，使用自定义文件处理器
   * @param zipFilePath ZIP文件的路径
   * @param outputFilePath 输出文件路径
   * @param fileHandler 文件处理器
   * @return 操作结果
   */
  public Try<Void> extractFile(String zipFilePath, String outputFilePath,
                               BiFunction<ZipEntryWrapper, OutputStream, Boolean> fileHandler) {
    return extractFile(new File(zipFilePath), outputFilePath, fileHandler);
  }

  /**
   * 解压ZIP文件中的指定文件到输出路径，使用自定义文件处理器
   * @param zipFile ZIP文件对象
   * @param outputFilePath 输出文件路径
   * @param fileHandler 文件处理器
   * @return 操作结果
   */
  public Try<Void> extractFile(File zipFile, String outputFilePath,
                               BiFunction<ZipEntryWrapper, OutputStream, Boolean> fileHandler) {
    if (!zipFile.exists() || !zipFile.isFile()) {
      return Try.failure(new FileNotFoundException("Zip file not found: " + zipFile.getAbsolutePath()));
    }

    try (var zip = new ZipFile(zipFile)) {
      List<Tuple2<String, Throwable>> failed = new ArrayList<>();

      var entries = zip.stream().collect(Collectors.toList());
      for (var entry : entries) {
        if (entry.isDirectory()) {
          continue;
        }

        // 安全检查
        var safetyCheck = validateZipEntry(entry, outputFilePath);
        if (safetyCheck.isFailure()) {
          failed.add(Tuple.of(entry.getName(), safetyCheck.getCause()));
          continue;
        }

        var targetFilePath = Paths.get(outputFilePath, entry.getName());
        var targetFile = targetFilePath.toFile();
        var targetDir = targetFile.getParentFile();

        if (!targetDir.exists()) {
          targetDir.mkdirs();
        }

        try (var outputStream = new FileOutputStream(targetFile);
             var inputStream = zip.getInputStream(entry)) {

          var wrapper = new ZipEntryWrapper(entry, inputStream);
          boolean success = fileHandler.apply(wrapper, outputStream);
          if (!success) {
            failed.add(Tuple.of(entry.getName(), new RuntimeException("File handler returned false")));
          }
        } catch (IOException e) {
          logger.error("Failed to extract file: {}", entry.getName(), e);
          failed.add(Tuple.of(entry.getName(), e));
        }
      }

      if (!failed.isEmpty()) {
        logger.warn("Failed to extract {} files", failed.size());
        failed.forEach(f -> logger.warn("  {}: {}", f._1(), f._2().getMessage()));
      }

      return extractionResult(failed);
    } catch (IOException e) {
      return Try.failure(e);
    }
  }

  /**
   * 从ZIP输入流中解压指定文件到输出路径
   * @param zipStream ZIP文件的输入流
   * @param outputFilePath 输出文件路径
   * @return 操作结果
   */
  public Try<Void> extractFile(InputStream zipStream, String outputFilePath) {
    return extractFile(zipStream, outputFilePath, (wrapper, outputStream) -> {
      try {
        wrapper.copyTo(outputStream);
        return true;
      } catch (IOException e) {
        logger.error("Failed to copy file: {}", wrapper.getName(), e);
        return false;
      }
    });
  }

  /**
   * 从ZIP输入流中解压指定文件到输出路径，使用自定义文件处理器
   * @param zipStream ZIP文件的输入流
   * @param outputFilePath 输出文件路径
   * @param fileHandler 文件处理器
   * @return 操作结果
   */
  public Try<Void> extractFile(InputStream zipStream, String outputFilePath,
                               BiFunction<ZipEntryWrapper, OutputStream, Boolean> fileHandler) {
    try (var zip = new ZipInputStream(zipStream)) {
      List<Tuple2<String, Throwable>> failed = new ArrayList<>();
      ZipEntry zipEntry;
      int entryCount = 0;

      while ((zipEntry = zip.getNextEntry()) != null) {
        if (++entryCount > MAX_ENTRIES) {
          return Try.failure(new IOException("Too many entries in zip file (max: " + MAX_ENTRIES + ")"));
        }

        if (zipEntry.isDirectory()) {
          zip.closeEntry();
          continue;
        }

        // 安全检查
        var safetyCheck = validateZipEntry(zipEntry, outputFilePath);
        if (safetyCheck.isFailure()) {
          failed.add(Tuple.of(zipEntry.getName(), safetyCheck.getCause()));
          zip.closeEntry();
          continue;
        }

        var targetFilePath = Paths.get(outputFilePath, zipEntry.getName());
        var targetFile = targetFilePath.toFile();
        var targetDir = targetFile.getParentFile();

        if (!targetDir.exists()) {
          targetDir.mkdirs();
        }

        try (var outputStream = new FileOutputStream(targetFile)) {
          var limitedStream = new LimitedZipEntryInputStream(zip, zipEntry.getSize());
          var wrapper = new ZipEntryWrapper(zipEntry, limitedStream);
          boolean success = fileHandler.apply(wrapper, outputStream);
          if (!success) {
            failed.add(Tuple.of(zipEntry.getName(), new RuntimeException("File handler returned false")));
          }
        } catch (IOException e) {
          logger.error("Failed to extract file: {}", zipEntry.getName(), e);
          failed.add(Tuple.of(zipEntry.getName(), e));
        } finally {
          zip.closeEntry();
        }
      }

      if (!failed.isEmpty()) {
        logger.warn("Failed to extract {} files from InputStream", failed.size());
        failed.forEach(f -> logger.warn("  {}: {}", f._1(), f._2().getMessage()));
      }

      return extractionResult(failed);
    } catch (IOException e) {
      return Try.failure(e);
    }
  }

  private Try<Void> extractionResult(List<Tuple2<String, Throwable>> failed) {
    if (failed.isEmpty()) {
      return Try.success(null);
    }
    IOException failure = new IOException("Failed to extract " + failed.size() + " ZIP entries");
    for (var entry : failed) {
      failure.addSuppressed(new IOException("Failed to extract entry: " + entry._1(), entry._2()));
    }
    return Try.failure(failure);
  }

  // ==================== 实用方法 ====================

  /**
   * 获取指定扩展名的文件列表
   */
  public List<String> getFilesByExtension(String zipFilePath, String... extensions) {
    return getFilesByExtension(new File(zipFilePath), extensions);
  }

  public List<String> getFilesByExtension(File zipFile, String... extensions) {
    Set<String> extSet = Arrays.stream(extensions)
        .map(ext -> ext.toLowerCase().startsWith(".") ? ext.toLowerCase() : "." + ext.toLowerCase())
        .collect(Collectors.toSet());

    return getFileList(zipFile).stream()
        .filter(filename -> {
          int lastDot = filename.lastIndexOf('.');
          if (lastDot == -1) {
            return false;
          }
          String ext = filename.substring(lastDot).toLowerCase();
          return extSet.contains(ext);
        })
        .collect(Collectors.toList());
  }

  /**
   * 获取指定路径前缀的文件列表
   */
  public List<String> getFilesByPrefix(String zipFilePath, String prefix) {
    return getFilesByPrefix(new File(zipFilePath), prefix);
  }

  public List<String> getFilesByPrefix(File zipFile, String prefix) {
    return getFileList(zipFile).stream()
        .filter(filename -> filename.startsWith(prefix))
        .collect(Collectors.toList());
  }

  /**
   * 计算zip文件中的总文件数和总大小
   */
  public ZipStatistics getZipStatistics(String zipFilePath) {
    return getZipStatistics(new File(zipFilePath));
  }

  public ZipStatistics getZipStatistics(File zipFile) {
    if (!zipFile.exists() || !zipFile.isFile()) {
      return new ZipStatistics(0, 0, 0, 0);
    }

    try (var zip = new ZipFile(zipFile)) {
      long totalSize = 0;
      long totalCompressedSize = 0;
      int fileCount = 0;
      int dirCount = 0;

      var entries = zip.entries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        if (entry.isDirectory()) {
          dirCount++;
        } else {
          fileCount++;
          totalSize += entry.getSize();
          totalCompressedSize += entry.getCompressedSize();
        }
      }

      return new ZipStatistics(fileCount, dirCount, totalSize, totalCompressedSize);
    } catch (IOException e) {
      logger.error("Failed to get zip statistics: {}", zipFile.getAbsolutePath(), e);
      return new ZipStatistics(0, 0, 0, 0);
    }
  }

  // ==================== 私有方法 ====================

  /**
   * 验证ZipEntry的安全性，防止路径遍历攻击
   */
  private Try<Void> validateZipEntry(ZipEntry entry, String outputPath) {
    String entryName = entry.getName();

    // 检查路径遍历
    if (entryName.contains("..") || entryName.startsWith("/") || entryName.contains("\\")) {
      return Try.failure(new SecurityException("Unsafe path detected: " + entryName));
    }

    // 检查文件大小
    if (entry.getSize() > MAX_ENTRY_SIZE) {
      return Try.failure(new IOException("Entry too large: " + entryName + " (" + entry.getSize() + " bytes)"));
    }

    // 检查解压后的路径是否在指定目录内
    try {
      Path outputDir = Paths.get(outputPath).toAbsolutePath().normalize();
      Path targetPath = outputDir.resolve(entryName).toAbsolutePath().normalize();

      if (!targetPath.startsWith(outputDir)) {
        return Try.failure(new SecurityException("Path traversal detected: " + entryName));
      }
    } catch (Exception e) {
      return Try.failure(new SecurityException("Invalid path: " + entryName, e));
    }

    return Try.success(null);
  }

  // ==================== 内部类 ====================

  /**
   * ZIP条目包装器，封装ZipEntry和对应的InputStream
   */
  public static class ZipEntryWrapper implements AutoCloseable {
    private final ZipEntry zipEntry;
    private final InputStream inputStream;

    public ZipEntryWrapper(ZipEntry zipEntry, InputStream inputStream) {
      this.zipEntry = zipEntry;
      this.inputStream = inputStream;
    }

    public ZipEntry getZipEntry() {
      return zipEntry;
    }

    public InputStream getInputStream() {
      return inputStream;
    }

    // 便利方法
    public String getName() {
      return zipEntry.getName();
    }

    public long getSize() {
      return zipEntry.getSize();
    }

    public long getCompressedSize() {
      return zipEntry.getCompressedSize();
    }

    public boolean isDirectory() {
      return zipEntry.isDirectory();
    }

    public long getLastModifiedTime() {
      return zipEntry.getTime();
    }

    public long getCrc() {
      return zipEntry.getCrc();
    }

    public double getCompressionRatio() {
      long size = getSize();
      long compressedSize = getCompressedSize();
      return size > 0 ? (1.0 - (double) compressedSize / size) * 100 : 0;
    }

    /**
     * 读取所有内容为字节数组
     */
    public byte[] readAllBytes() throws IOException {
      return IOUtils.toByteArray(inputStream);
    }

    /**
     * 读取所有内容为字符串
     */
    public String readAsString() throws IOException {
      return readAsString(StandardCharsets.UTF_8.name());
    }

    /**
     * 读取所有内容为指定编码的字符串
     */
    public String readAsString(String charset) throws IOException {
      byte[] bytes = readAllBytes();
      return new String(bytes, charset);
    }

    /**
     * 复制内容到输出流
     */
    public long copyTo(OutputStream outputStream) throws IOException {
      return IOUtils.copyLarge(inputStream, outputStream);
    }

    @Override
    public void close() throws IOException {
      // 注意：对于LimitedZipEntryInputStream，close()方法不会关闭底层流
      inputStream.close();
    }

    @Override
    public String toString() {
      return String.format("ZipEntryWrapper{name='%s', size=%d, compressed=%d, isDir=%s}",
          getName(), getSize(), getCompressedSize(), isDirectory());
    }
  }

  /**
   * ZIP条目信息
   */
  public static class ZipEntryInfo {
    private final String name;
    private final long size;
    private final long compressedSize;
    private final boolean isDirectory;
    private final long lastModified;
    private final long crc;

    public ZipEntryInfo(String name, long size, long compressedSize, boolean isDirectory, long lastModified, long crc) {
      this.name = name;
      this.size = size;
      this.compressedSize = compressedSize;
      this.isDirectory = isDirectory;
      this.lastModified = lastModified;
      this.crc = crc;
    }

    // Getters
    public String getName() { return name; }
    public long getSize() { return size; }
    public long getCompressedSize() { return compressedSize; }
    public boolean isDirectory() { return isDirectory; }
    public long getLastModified() { return lastModified; }
    public long getCrc() { return crc; }
    public double getCompressionRatio() {
      return size > 0 ? (1.0 - (double) compressedSize / size) * 100 : 0;
    }

    @Override
    public String toString() {
      return String.format("ZipEntryInfo{name='%s', size=%d, compressed=%d, ratio=%.1f%%, isDir=%s}",
          name, size, compressedSize, getCompressionRatio(), isDirectory);
    }
  }

  /**
   * ZIP文件统计信息
   */
  public static class ZipStatistics {
    private final int fileCount;
    private final int directoryCount;
    private final long totalSize;
    private final long totalCompressedSize;

    public ZipStatistics(int fileCount, int directoryCount, long totalSize, long totalCompressedSize) {
      this.fileCount = fileCount;
      this.directoryCount = directoryCount;
      this.totalSize = totalSize;
      this.totalCompressedSize = totalCompressedSize;
    }

    public int getFileCount() { return fileCount; }
    public int getDirectoryCount() { return directoryCount; }
    public long getTotalSize() { return totalSize; }
    public long getTotalCompressedSize() { return totalCompressedSize; }
    public double getOverallCompressionRatio() {
      return totalSize > 0 ? (1.0 - (double) totalCompressedSize / totalSize) * 100 : 0;
    }

    @Override
    public String toString() {
      return String.format("ZipStatistics{files=%d, dirs=%d, size=%d bytes, compressed=%d bytes, ratio=%.1f%%}",
          fileCount, directoryCount, totalSize, totalCompressedSize, getOverallCompressionRatio());
    }
  }

  /**
   * 限制读取范围的InputStream包装器（修复版）
   */
  private static class LimitedZipEntryInputStream extends InputStream {
    private final ZipInputStream zipInputStream;
    private final long maxBytes;
    private long bytesRead = 0;
    private boolean closed = false;

    public LimitedZipEntryInputStream(ZipInputStream zipInputStream, long maxBytes) {
      this.zipInputStream = zipInputStream;
      this.maxBytes = maxBytes > 0 ? maxBytes : Long.MAX_VALUE; // 处理-1的情况
    }

    @Override
    public int read() throws IOException {
      if (closed || bytesRead >= maxBytes) {
        return -1;
      }

      int result = zipInputStream.read();
      if (result != -1) {
        bytesRead++;
      }
      return result;
    }

    @Override
    public int read(byte[] b) throws IOException {
      return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (closed || bytesRead >= maxBytes) {
        return -1;
      }

      long remainingBytes = maxBytes - bytesRead;
      if (remainingBytes < len) {
        len = (int) remainingBytes;
      }
      if (len <= 0) {
        return -1;
      }

      int result = zipInputStream.read(b, off, len);
      if (result > 0) {
        bytesRead += result;
      }
      return result;
    }

    @Override
    public long skip(long n) throws IOException {
      if (closed || bytesRead >= maxBytes) {
        return 0;
      }

      long remainingBytes = maxBytes - bytesRead;
      if (n > remainingBytes) {
        n = remainingBytes;
      }

      long result = zipInputStream.skip(n);
      if (result > 0) {
        bytesRead += result;
      }
      return result;
    }

    @Override
    public int available() throws IOException {
      if (closed || bytesRead >= maxBytes) {
        return 0;
      }

      int available = zipInputStream.available();
      long remainingBytes = maxBytes - bytesRead;
      if (available > remainingBytes) {
        available = (int) remainingBytes;
      }
      return available;
    }

    @Override
    public void close() {
      closed = true;
      // 不关闭底层ZipInputStream
    }

    public long getBytesRead() {
      return bytesRead;
    }
  }
}
