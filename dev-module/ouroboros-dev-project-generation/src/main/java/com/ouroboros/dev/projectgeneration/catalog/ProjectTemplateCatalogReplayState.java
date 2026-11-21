package com.ouroboros.dev.projectgeneration.catalog;

public record ProjectTemplateCatalogReplayState(
    long sequence,
    String digest,
    long version
) {
  public ProjectTemplateCatalogReplayState {
    if (sequence < 1) {
      throw new IllegalArgumentException("Catalog sequence must be positive");
    }
    if (digest == null || !digest.matches("^[a-f0-9]{64}$")) {
      throw new IllegalArgumentException("Catalog digest must be lowercase SHA-256 hex");
    }
    if (version < 1) {
      throw new IllegalArgumentException("Replay state version must be positive");
    }
  }
}
