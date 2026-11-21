package com.ouroboros.upload.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
public class StartChunkRequest {
  @JsonProperty("filename")
  private String fileName;
  private String name;

  /**
   * 上传文件的路径
   */
  public String getFileName() {
    return fileName;
  }

  /**
   * 上传文件的路径
   */
  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  /**
   * 上传文件的名称
   */
  public String getName() {
    return name;
  }

  /**
   * 上传文件的名称
   */
  public void setName(String name) {
    this.name = name;
  }
}
