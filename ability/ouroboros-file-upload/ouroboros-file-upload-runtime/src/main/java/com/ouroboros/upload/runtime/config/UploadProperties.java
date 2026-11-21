package com.ouroboros.upload.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ouroboros.upload")
public class UploadProperties {

  // 存储类型（fileSystem, aliyun, minio, tencent，huawei，qiniu等等）
  private FileStorageType storageType = FileStorageType.FILESYSTEM;

  // 文件系统存储方式配置
  private final FileSystemStorageProperties fileSystem = new FileSystemStorageProperties();

  // 文件存储路径策略
  private String filePathStrategy = "default";

  // 文件存储路径前缀
  private String filePathPrefix = "";

  // 文件路径模版
  private String filePathTemplate = "{yyyy}/{MM}/{dd}";

  // 存储文件名策略
  private String fileNameStrategy = "default";

  // 存储文件名模版
  private String fileNameTemplate = "{rawName}-{timestamp}.{ext}";

  // 生成 url 策略
  private String fileUrlStrategy = "default";

  // url 前缀
  private String fileUrlPrefix = "/api/file";

  // 匿名访问前缀
  private String[] anonymousPrefixes = new String[]{"**"};

  public FileStorageType getStorageType() {
    return storageType;
  }

  public UploadProperties setStorageType(FileStorageType storageType) {
    this.storageType = storageType;
    return this;
  }

  public String getFilePathStrategy() {
    return filePathStrategy;
  }

  public UploadProperties setFilePathStrategy(String filePathStrategy) {
    this.filePathStrategy = filePathStrategy;
    return this;
  }

  public String getFilePathPrefix() {
    return filePathPrefix;
  }

  public UploadProperties setFilePathPrefix(String filePathPrefix) {
    this.filePathPrefix = filePathPrefix;
    return this;
  }

  public String getFilePathTemplate() {
    return filePathTemplate;
  }

  public UploadProperties setFilePathTemplate(String filePathTemplate) {
    this.filePathTemplate = filePathTemplate;
    return this;
  }

  public String getFileNameStrategy() {
    return fileNameStrategy;
  }

  public UploadProperties setFileNameStrategy(String fileNameStrategy) {
    this.fileNameStrategy = fileNameStrategy;
    return this;
  }

  public String getFileNameTemplate() {
    return fileNameTemplate;
  }

  public UploadProperties setFileNameTemplate(String fileNameTemplate) {
    this.fileNameTemplate = fileNameTemplate;
    return this;
  }

  public String getFileUrlStrategy() {
    return fileUrlStrategy;
  }

  public UploadProperties setFileUrlStrategy(String fileUrlStrategy) {
    this.fileUrlStrategy = fileUrlStrategy;
    return this;
  }

  public String getFileUrlPrefix() {
    return fileUrlPrefix;
  }

  public UploadProperties setFileUrlPrefix(String fileUrlPrefix) {
    this.fileUrlPrefix = fileUrlPrefix;
    return this;
  }

  public FileSystemStorageProperties getFileSystem() {
    return fileSystem;
  }

  public String[] getAnonymousPrefixes() {
    return anonymousPrefixes;
  }

  public UploadProperties setAnonymousPrefixes(String[] anonymousPrefixes) {
    this.anonymousPrefixes = anonymousPrefixes;
    return this;
  }
}
