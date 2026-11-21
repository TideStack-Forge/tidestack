package com.ouroboros.util;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Try;

@SuppressWarnings({"unused", "UnusedReturnValue", "resource"})
public class ZipWriter implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(ZipWriter.class);
  private static final int DEFAULT_BUFFER_SIZE = 8192;
  // 1GB
  private static final long MAX_TOTAL_SIZE = 1024 * 1024 * 1024;
  private static final int MAX_ENTRIES = 50000;
  private final Set<String> addedEntries = new HashSet<>();
  private final ZipWriterConfig config;
  private final ZipOutputStream zipOutputStream;
  private boolean closed = false;
  private long totalBytesWritten = 0;
  private int entryCount = 0;

  // ==================== 构造方法 ====================

  public ZipWriter(String outputFilePath) throws IOException {
    this(Files.newOutputStream(Paths.get(outputFilePath)), new ZipWriterConfig());
  }

  public ZipWriter(File outputFile) throws IOException {
    this(Files.newOutputStream(outputFile.toPath()), new ZipWriterConfig());
  }

  public ZipWriter(OutputStream outputStream) {
    this(outputStream, new ZipWriterConfig());
  }

  public ZipWriter(String outputFilePath, ZipWriterConfig config) throws IOException {
    this(Files.newOutputStream(Paths.get(outputFilePath)), config);
  }

  public ZipWriter(File outputFile, ZipWriterConfig config) throws IOException {
    this(Files.newOutputStream(outputFile.toPath()), config);
  }

  public ZipWriter(OutputStream outputStream, ZipWriterConfig config) {
    this.config = config;
    this.zipOutputStream = new ZipOutputStream(outputStream);
    this.zipOutputStream.setLevel(config.compressionLevel);
    if (config.comment != null) {
      this.zipOutputStream.setComment(config.comment);
    }
  }

  // ==================== 静态创建方法 ====================

  public static ZipWriter toFile(String outputFilePath) throws IOException {
    return new ZipWriter(outputFilePath);
  }

  public static ZipWriter toFile(File outputFile) throws IOException {
    return new ZipWriter(outputFile);
  }

  public static ZipWriter toStream(OutputStream outputStream) {
    return new ZipWriter(outputStream);
  }

  public static ZipWriterBuilder builder() {
    return new ZipWriterBuilder();
  }

  // ==================== 添加文件方法 ====================

  /**
   * 快速创建zip文件
   */
  public static WriteResult createZip(String outputPath, String... filePaths) {
    return Try.of(() -> {
          try (ZipWriter writer = new ZipWriter(outputPath)) {
            writer.addFiles(filePaths);
            return new WriteResult(true, writer.getEntryCount(), writer.getTotalBytesWritten(), null);
          }
        }).recover(e -> new WriteResult(false, 0, 0, e.getMessage()))
        .get();
  }

  public static WriteResult createZip(String outputPath, Map<String, String> stringEntries) {
    return Try.of(() -> {
          try (ZipWriter writer = new ZipWriter(outputPath)) {
            writer.addStringEntries(stringEntries);
            return new WriteResult(true, writer.getEntryCount(), writer.getTotalBytesWritten(), null);
          }
        }).recover(e -> new WriteResult(false, 0, 0, e.getMessage()))
        .get();
  }

  public static byte[] createZipBytes(Map<String, byte[]> dataEntries) {
    return Try.of(() -> {
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
           ZipWriter writer = new ZipWriter(baos)) {
        writer.addEntries(dataEntries);
        writer.finish();
        return baos.toByteArray();
      }
    }).getOrElse(new byte[0]);
  }

  /**
   * 添加单个文件
   */
  public ZipWriter addFile(String filePath) throws IOException {
    return addFile(new File(filePath));
  }

  public ZipWriter addFile(File file) throws IOException {
    return addFile(file, file.getName());
  }

  public ZipWriter addFile(String filePath, String entryName) throws IOException {
    return addFile(new File(filePath), entryName);
  }

  public ZipWriter addFile(File file, String entryName) throws IOException {
    if (!file.exists() || !file.isFile()) {
      throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
    }

    try (FileInputStream fis = new FileInputStream(file)) {
      return addEntry(entryName, fis, file.length());
    }
  }

  /**
   * 添加目录及其所有内容
   */
  public ZipWriter addDirectory(String directoryPath) throws IOException {
    return addDirectory(new File(directoryPath));
  }

  public ZipWriter addDirectory(File directory) throws IOException {
    return addDirectory(directory, "");
  }

  public ZipWriter addDirectory(String directoryPath, String baseEntryName) throws IOException {
    return addDirectory(new File(directoryPath), baseEntryName);
  }

  public ZipWriter addDirectory(File directory, String baseEntryName) throws IOException {
    return addDirectory(directory, baseEntryName, file -> true);
  }

  public ZipWriter addDirectory(File directory, String baseEntryName, Predicate<File> filter) throws IOException {
    if (!directory.exists() || !directory.isDirectory()) {
      throw new FileNotFoundException("Directory not found: " + directory.getAbsolutePath());
    }

    Path basePath = directory.toPath();

    Files.walkFileTree(basePath, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        File fileObj = file.toFile();
        if (filter.test(fileObj)) {
          String relativePath = basePath.relativize(file).toString();
          String entryName = baseEntryName.isEmpty() ? relativePath : baseEntryName + "/" + relativePath;
          entryName = entryName.replace("\\", "/"); // 统一使用Unix风格路径

          addFile(fileObj, entryName);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (!dir.equals(basePath)) {
          String relativePath = basePath.relativize(dir).toString();
          String entryName = baseEntryName.isEmpty() ? relativePath : baseEntryName + "/" + relativePath;
          entryName = entryName.replace("\\", "/") + "/";

          addEmptyDirectory(entryName);
        }
        return FileVisitResult.CONTINUE;
      }
    });

    return this;
  }

  /**
   * 添加空目录
   */
  public ZipWriter addEmptyDirectory(String directoryName) throws IOException {
    String entryName = directoryName.endsWith("/") ? directoryName : directoryName + "/";
    return addEntry(entryName, null, 0);
  }

  /**
   * 从字节数组添加条目
   */
  public ZipWriter addEntry(String entryName, byte[] data) throws IOException {
    return addEntry(entryName, new ByteArrayInputStream(data), data.length);
  }

  /**
   * 从字符串添加条目
   */
  public ZipWriter addEntry(String entryName, String content) throws IOException {
    return addEntry(entryName, content, StandardCharsets.UTF_8);
  }

  public ZipWriter addEntry(String entryName, String content, String charset) throws IOException {
    byte[] data = content.getBytes(charset);
    return addEntry(entryName, data);
  }

  // ==================== 批量操作方法 ====================

  public ZipWriter addEntry(String entryName, String content, Charset charset) throws IOException {
    byte[] data = content.getBytes(charset);
    return addEntry(entryName, data);
  }

  /**
   * 从输入流添加条目
   */
  public ZipWriter addEntry(String entryName, InputStream inputStream) throws IOException {
    return addEntry(entryName, inputStream, -1);
  }

  public ZipWriter addEntry(String entryName, InputStream inputStream, long size) throws IOException {
    validateState();
    validateEntryName(entryName);

    if (addedEntries.contains(entryName)) {
      if (config.allowDuplicateEntries) {
        logger.warn("Duplicate entry: {}", entryName);
        return this;
      } else {
        throw new IllegalArgumentException("Duplicate entry: " + entryName);
      }
    }

    if (++entryCount > MAX_ENTRIES) {
      throw new IOException("Too many entries (max: " + MAX_ENTRIES + ")");
    }

    ZipEntry zipEntry = new ZipEntry(entryName);
    if (config.fixedEntryTimestamp != null) {
      zipEntry.setTime(config.fixedEntryTimestamp.toEpochMilli());
    }
    if (size >= 0) {
      zipEntry.setSize(size);
    }

    zipOutputStream.putNextEntry(zipEntry);

    if (inputStream != null && !entryName.endsWith("/")) {
      long bytesWritten = IOUtils.copyLarge(inputStream, zipOutputStream);
      totalBytesWritten += bytesWritten;

      if (totalBytesWritten > MAX_TOTAL_SIZE) {
        throw new IOException("Total size too large (max: " + MAX_TOTAL_SIZE + " bytes)");
      }

      if (config.progressCallback != null) {
        config.progressCallback.accept(entryName, bytesWritten);
      }
    }

    zipOutputStream.closeEntry();
    addedEntries.add(entryName);

    logger.debug("Added entry: {} ({} bytes)", entryName, size);
    return this;
  }

  /**
   * 批量添加文件
   */
  public ZipWriter addFiles(String... filePaths) throws IOException {
    for (String filePath : filePaths) {
      addFile(filePath);
    }
    return this;
  }

  public ZipWriter addFiles(File... files) throws IOException {
    for (File file : files) {
      addFile(file);
    }
    return this;
  }

  public ZipWriter addFiles(Map<String, File> fileEntries) throws IOException {
    for (Map.Entry<String, File> entry : fileEntries.entrySet()) {
      addFile(entry.getValue(), entry.getKey());
    }
    return this;
  }

  // ==================== 实用方法 ====================

  /**
   * 批量添加内存数据
   */
  public ZipWriter addEntries(Map<String, byte[]> dataEntries) throws IOException {
    for (Map.Entry<String, byte[]> entry : dataEntries.entrySet()) {
      addEntry(entry.getKey(), entry.getValue());
    }
    return this;
  }

  public ZipWriter addStringEntries(Map<String, String> stringEntries) throws IOException {
    for (Map.Entry<String, String> entry : stringEntries.entrySet()) {
      addEntry(entry.getKey(), entry.getValue());
    }
    return this;
  }

  /**
   * 使用自定义数据源添加条目
   */
  public ZipWriter addEntries(List<ZipEntryData> entryDataList) throws IOException {
    for (ZipEntryData entryData : entryDataList) {
      try (InputStream is = entryData.getInputStream()) {
        addEntry(entryData.getEntryName(), is, entryData.getSize());
      }
    }
    return this;
  }

  /**
   * 获取已添加的条目数量
   */
  public int getEntryCount() {
    return entryCount;
  }

  /**
   * 获取已写入的总字节数
   */
  public long getTotalBytesWritten() {
    return totalBytesWritten;
  }

  /**
   * 获取已添加的条目名称列表
   */
  public Set<String> getAddedEntries() {
    return Collections.unmodifiableSet(addedEntries);
  }

  /**
   * 检查条目是否已存在
   */
  public boolean hasEntry(String entryName) {
    return addedEntries.contains(entryName);
  }

  // ==================== 静态工具方法 ====================

  /**
   * 设置压缩级别
   */
  public ZipWriter setCompressionLevel(int level) {
    if (level < Deflater.NO_COMPRESSION || level > Deflater.BEST_COMPRESSION) {
      throw new IllegalArgumentException("Invalid compression level: " + level);
    }
    zipOutputStream.setLevel(level);
    return this;
  }

  /**
   * 设置注释
   */
  public ZipWriter setComment(String comment) {
    zipOutputStream.setComment(comment);
    return this;
  }

  /**
   * 完成写入（不关闭流）
   */
  public ZipWriter finish() throws IOException {
    validateState();
    zipOutputStream.finish();
    return this;
  }

  // ==================== 私有方法 ====================

  private void validateState() throws IOException {
    if (closed) {
      throw new IOException("ZipWriter is closed");
    }
  }

  private void validateEntryName(String entryName) {
    if (entryName == null || entryName.trim().isEmpty()) {
      throw new IllegalArgumentException("Entry name cannot be null or empty");
    }

    // 安全检查：防止路径遍历
    if (entryName.contains("..") || entryName.startsWith("/")) {
      throw new IllegalArgumentException("Unsafe entry name: " + entryName);
    }
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      try {
        zipOutputStream.close();
        logger.info("ZipWriter closed. Total entries: {}, Total bytes: {}", entryCount, totalBytesWritten);
      } finally {
        closed = true;
      }
    }
  }

  // ==================== 内部类 ====================

  /**
   * ZIP条目数据
   */
  public static class ZipEntryData {
    private final String entryName;
    private final Supplier<InputStream> inputStreamSupplier;
    private final long size;

    public ZipEntryData(String entryName, Supplier<InputStream> inputStreamSupplier, long size) {
      this.entryName = entryName;
      this.inputStreamSupplier = inputStreamSupplier;
      this.size = size;
    }

    public static ZipEntryData fromBytes(String entryName, byte[] data) {
      return new ZipEntryData(entryName, () -> new ByteArrayInputStream(data), data.length);
    }

    public static ZipEntryData fromString(String entryName, String content) {
      byte[] data = content.getBytes(StandardCharsets.UTF_8);
      return fromBytes(entryName, data);
    }

    public static ZipEntryData fromFile(String entryName, File file) {
      return new ZipEntryData(entryName, () -> {
        try {
          return new FileInputStream(file);
        } catch (FileNotFoundException e) {
          throw new RuntimeException(e);
        }
      }, file.length());
    }

    public String getEntryName() {
      return entryName;
    }

    public InputStream getInputStream() {
      return inputStreamSupplier.get();
    }

    public long getSize() {
      return size;
    }
  }

  /**
   * ZIP写入配置
   */
  public static class ZipWriterConfig {
    private int compressionLevel = Deflater.DEFAULT_COMPRESSION;
    private String comment = null;
    private boolean allowDuplicateEntries = false;
    private BiConsumer<String, Long> progressCallback = null;
    private Instant fixedEntryTimestamp = null;

    public ZipWriterConfig setCompressionLevel(int compressionLevel) {
      this.compressionLevel = compressionLevel;
      return this;
    }

    public ZipWriterConfig setComment(String comment) {
      this.comment = comment;
      return this;
    }

    public ZipWriterConfig setAllowDuplicateEntries(boolean allowDuplicateEntries) {
      this.allowDuplicateEntries = allowDuplicateEntries;
      return this;
    }

    public ZipWriterConfig setProgressCallback(BiConsumer<String, Long> progressCallback) {
      this.progressCallback = progressCallback;
      return this;
    }

    public ZipWriterConfig setFixedEntryTimestamp(Instant fixedEntryTimestamp) {
      this.fixedEntryTimestamp = fixedEntryTimestamp;
      return this;
    }
  }

  /**
   * 建造者模式
   */
  public static class ZipWriterBuilder {
    private final ZipWriterConfig config = new ZipWriterConfig();
    private OutputStream outputStream;
    private String outputFilePath;

    public ZipWriterBuilder compressionLevel(int level) {
      config.setCompressionLevel(level);
      return this;
    }

    public ZipWriterBuilder comment(String comment) {
      config.setComment(comment);
      return this;
    }

    public ZipWriterBuilder allowDuplicateEntries(boolean allow) {
      config.setAllowDuplicateEntries(allow);
      return this;
    }

    public ZipWriterBuilder progressCallback(BiConsumer<String, Long> callback) {
      config.setProgressCallback(callback);
      return this;
    }

    public ZipWriterBuilder fixedEntryTimestamp(Instant timestamp) {
      config.setFixedEntryTimestamp(timestamp);
      return this;
    }

    public ZipWriterBuilder outputTo(String filePath) {
      this.outputFilePath = filePath;
      return this;
    }

    public ZipWriterBuilder outputTo(OutputStream outputStream) {
      this.outputStream = outputStream;
      return this;
    }

    public ZipWriter build() throws IOException {
      if (outputStream != null) {
        return new ZipWriter(outputStream, config);
      } else if (outputFilePath != null) {
        return new ZipWriter(outputFilePath, config);
      } else {
        throw new IllegalStateException("Output destination not specified");
      }
    }
  }

  /**
   * 写入结果
   */
  public static class WriteResult {
    private final boolean success;
    private final int entryCount;
    private final long totalBytes;
    private final String errorMessage;

    public WriteResult(boolean success, int entryCount, long totalBytes, String errorMessage) {
      this.success = success;
      this.entryCount = entryCount;
      this.totalBytes = totalBytes;
      this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
      return success;
    }

    public int getEntryCount() {
      return entryCount;
    }

    public long getTotalBytes() {
      return totalBytes;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    @Override
    public String toString() {
      if (success) {
        return String.format("WriteResult{success=true, entries=%d, bytes=%d}", entryCount, totalBytes);
      } else {
        return String.format("WriteResult{success=false, error='%s'}", errorMessage);
      }
    }
  }
}
