package com.ouroboros.dev.projectgeneration.catalog;

import java.net.URI;

public record ProjectTemplateArtifactDescriptor(
    URI uri,
    String sha256,
    long compressedSize
) {
  public ProjectTemplateArtifactDescriptor {
    if (uri == null) {
      throw new IllegalArgumentException("Artifact URI is required");
    }
    if (sha256 == null || !sha256.matches("^[a-f0-9]{64}$")) {
      throw new IllegalArgumentException("Artifact SHA-256 must be lowercase hex");
    }
    if (compressedSize < 1) {
      throw new IllegalArgumentException("Artifact compressed size must be positive");
    }
  }
}
