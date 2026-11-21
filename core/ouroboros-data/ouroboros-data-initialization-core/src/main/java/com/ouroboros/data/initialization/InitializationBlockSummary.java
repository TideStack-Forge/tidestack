package com.ouroboros.data.initialization;

public record InitializationBlockSummary(
    int inserted,
    int skipped
) {
  public InitializationBlockSummary plus(InitializationBlockSummary other) {
    return new InitializationBlockSummary(
        inserted + other.inserted,
        skipped + other.skipped
    );
  }
}
