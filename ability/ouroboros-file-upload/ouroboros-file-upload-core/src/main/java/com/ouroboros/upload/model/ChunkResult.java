package com.ouroboros.upload.model;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
public class ChunkResult {
  private String eTag;

  public ChunkResult() {
  }

  public ChunkResult(String eTag) {
    this.eTag = eTag;
  }

  /**
   * 上传文件的eTag
   */
  @JsonProperty("eTag")
  public String getETag() {
    return eTag;
  }

  /**
   * 上传文件的eTag
   */
  @JsonProperty("eTag")
  public void setETag(String eTag) {
    this.eTag = eTag;
  }
}
