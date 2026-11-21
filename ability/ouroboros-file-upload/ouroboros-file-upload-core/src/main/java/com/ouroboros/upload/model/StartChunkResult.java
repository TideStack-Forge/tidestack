package com.ouroboros.upload.model;

@SuppressWarnings("unused")
public class StartChunkResult {
  private String uploadId;
  private String key;

  public StartChunkResult() {
  }

  public StartChunkResult(String key, String uploadId) {
    this.key = key;
    this.uploadId = uploadId;
  }

  /**
   * 上传id
   */
  public String getUploadId() {
    return uploadId;
  }

  /**
   * 上传id
   */
  public void setUploadId(String uploadId) {
    this.uploadId = uploadId;
  }

  /**
   * 上传文件的key，一般是文件的路径
   */
  public String getKey() {
    return key;
  }

  /**
   * 上传文件的key，一般是文件的路径
   */
  public void setKey(String key) {
    this.key = key;
  }
}
