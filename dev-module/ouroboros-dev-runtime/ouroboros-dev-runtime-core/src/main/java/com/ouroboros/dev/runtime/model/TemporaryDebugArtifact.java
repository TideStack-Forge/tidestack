package com.ouroboros.dev.runtime.model;

import java.time.Instant;

public class TemporaryDebugArtifact {
  private final String uploadBatchId;
  private final String artifactId;
  private final String fileName;
  private final String relativePath;
  private final String sha256;
  private final Long sizeBytes;
  private final Instant createdAt;

  private TemporaryDebugArtifact(Builder builder) {
    this.uploadBatchId = builder.uploadBatchId;
    this.artifactId = builder.artifactId;
    this.fileName = builder.fileName;
    this.relativePath = builder.relativePath;
    this.sha256 = builder.sha256;
    this.sizeBytes = builder.sizeBytes;
    this.createdAt = builder.createdAt;
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getUploadBatchId() {
    return uploadBatchId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getRelativePath() {
    return relativePath;
  }

  public String getSha256() {
    return sha256;
  }

  public Long getSizeBytes() {
    return sizeBytes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public static class Builder {
    private String uploadBatchId;
    private String artifactId;
    private String fileName;
    private String relativePath;
    private String sha256;
    private Long sizeBytes;
    private Instant createdAt;

    private Builder() {
    }

    public Builder uploadBatchId(String uploadBatchId) {
      this.uploadBatchId = uploadBatchId;
      return this;
    }

    public Builder artifactId(String artifactId) {
      this.artifactId = artifactId;
      return this;
    }

    public Builder fileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    public Builder relativePath(String relativePath) {
      this.relativePath = relativePath;
      return this;
    }

    public Builder sha256(String sha256) {
      this.sha256 = sha256;
      return this;
    }

    public Builder sizeBytes(Long sizeBytes) {
      this.sizeBytes = sizeBytes;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public TemporaryDebugArtifact build() {
      return new TemporaryDebugArtifact(this);
    }
  }
}
