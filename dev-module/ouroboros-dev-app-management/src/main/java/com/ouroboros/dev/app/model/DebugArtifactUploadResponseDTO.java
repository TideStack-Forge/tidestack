package com.ouroboros.dev.app.model;

import java.util.ArrayList;
import java.util.List;

public class DebugArtifactUploadResponseDTO {
  private String uploadBatchId;
  private final List<TemporaryDebugArtifactDTO> artifacts = new ArrayList<TemporaryDebugArtifactDTO>();

  public String getUploadBatchId() {
    return uploadBatchId;
  }

  public void setUploadBatchId(String uploadBatchId) {
    this.uploadBatchId = uploadBatchId;
  }

  public List<TemporaryDebugArtifactDTO> getArtifacts() {
    return artifacts;
  }
}
