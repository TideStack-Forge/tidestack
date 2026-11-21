package com.ouroboros.upload.model.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
public class FinishChunkRequest {
  private String uploadId;
  private String key;
  private String fileName;
  private List<ChunkPart> chunkPartList;

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
   * 上传文件的名称
   */
  @JsonProperty("filename")
  public String getFileName() {
    return fileName;
  }

  /**
   * 上传文件的名称
   */
  @JsonProperty("filename")
  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  /**
   * 上传文件的分片列表
   */
  public List<ChunkPart> getPartList() {
    return chunkPartList;
  }

  /**
   * 上传文件的分片列表
   */
  public void setPartList(List<ChunkPart> chunkPartList) {
    this.chunkPartList = chunkPartList;
  }
}
