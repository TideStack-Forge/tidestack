package com.ouroboros.documentmanagement.storage;

import java.io.InputStream;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 文件存储服务接口
 *
 * 负责文件内容的实际存储，与元数据存储分离
 * 可以有多种实现：本地文件系统、对象存储（OSS/S3/MinIO）等
 */
public interface StorageService {
  /**
   * 存储文件
   *
   * @param inputStream 文件输入流
   * @param size        文件大小（字节）
   * @param mimeType    MIME 类型
   * @return 存储路径（用于后续检索）
   */
  StorageResult store(InputStream inputStream, Long size, String mimeType);

  /**
   * 读取文件
   *
   * @param storagePath 存储路径
   * @return 文件输入流
   */
  Optional<RetrievedFile> retrieve(String storagePath);

  /**
   * 删除文件
   *
   * @param storagePath 存储路径
   */
  void delete(String storagePath);

  /**
   * 检查文件是否存在
   *
   * @param storagePath 存储路径
   * @return 是否存在
   */
  boolean exists(String storagePath);

  /**
   * 获取文件大小
   *
   * @param storagePath 存储路径
   * @return 文件大小（字节），如果文件不存在返回 Optional.empty()
   */
  Optional<Long> getSize(String storagePath);

  /**
   * 复制文件
   *
   * @param sourcePath 源路径
   * @param targetPath 目标路径
   */
  void copy(String sourcePath, String targetPath);

  /**
   * 移动文件
   *
   * @param sourcePath 源路径
   * @param targetPath 目标路径
   */
  void move(String sourcePath, String targetPath);

  /**
   * 存储结果
   */
  class StorageResult {
    private final String storagePath;
    private final Long size;
    private final String checksum;

    public StorageResult(String storagePath, Long size, String checksum) {
      this.storagePath = storagePath;
      this.size = size;
      this.checksum = checksum;
    }

    public String getStoragePath() {
      return storagePath;
    }

    public Long getSize() {
      return size;
    }

    public String getChecksum() {
      return checksum;
    }
  }

  /**
   * 文件读取结果，包含 InputStream 和元信息
   */
  class RetrievedFile {
    private final Supplier<InputStream> inputStreamSupplier;
    private final long size;
    private final String mimeType;
    private final String checksum; // 可选字段，可用于校验

    public RetrievedFile(Supplier<InputStream> inputStreamSupplier, long size, String mimeType) {
      this(inputStreamSupplier, size, mimeType, null);
    }

    public RetrievedFile(Supplier<InputStream> inputStreamSupplier, long size, String mimeType, String checksum) {
      this.inputStreamSupplier = inputStreamSupplier;
      this.size = size;
      this.mimeType = mimeType;
      this.checksum = checksum;
    }

    public InputStream getInputStream() {
      return inputStreamSupplier.get();
    }

    public long getSize() {
      return size;
    }

    public String getMimeType() {
      return mimeType;
    }

    public String getChecksum() {
      return checksum;
    }
  }
}
