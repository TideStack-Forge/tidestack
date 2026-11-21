package com.ouroboros.upload.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
public class ChunkPart {
  private Integer partNumber;
  private String eTag;

  public Integer getPartNumber() {
    return partNumber;
  }

  public void setPartNumber(Integer partNumber) {
    this.partNumber = partNumber;
  }

  @JsonProperty("eTag")
  public String getETag() {
    return eTag;
  }

  @JsonProperty("eTag")
  public void setETag(String eTag) {
    this.eTag = eTag;
  }
}
