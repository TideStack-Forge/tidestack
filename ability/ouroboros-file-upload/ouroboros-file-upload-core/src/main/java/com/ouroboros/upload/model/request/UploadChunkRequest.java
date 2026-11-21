package com.ouroboros.upload.model.request;

import java.io.InputStream;

@SuppressWarnings("unused")
public class UploadChunkRequest {
  private String uploadId;
  private String key;
  private Integer partNumber;
  private Long partSize;
  private InputStream partContent;

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

  /**
   * 分片序号
   */
  public Integer getPartNumber() {
    return partNumber;
  }

  /**
   * 分片序号
   */
  public void setPartNumber(Integer partNumber) {
    this.partNumber = partNumber;
  }

  /**
   * 分片大小（字节，一般不用设置）
   */
  public Long getPartSize() {
    return partSize;
  }

  /**
   * 分片大小（字节，一般不用设置）
   */
  public void setPartSize(Long partSize) {
    this.partSize = partSize;
  }

  /**
   * 分片内容
   */
  public InputStream getPartContent() {
    return partContent;
  }

  /**
   * 分片内容
   */
  public void setPartContent(InputStream partContent) {
    this.partContent = partContent;
  }
}
