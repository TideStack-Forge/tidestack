package com.ouroboros.data.initialization;

public record InitializationResult(
    int plans,
    int blocks,
    int skippedBlocks,
    int inserted,
    int skipped
) {
  static InitializationResult empty() {
    return new InitializationResult(0, 0, 0, 0, 0);
  }

  InitializationResult withPlan() {
    return new InitializationResult(plans + 1, blocks, skippedBlocks, inserted, skipped);
  }

  InitializationResult withSkippedBlock() {
    return new InitializationResult(plans, blocks + 1, skippedBlocks + 1, inserted, skipped);
  }

  InitializationResult withAppliedBlock(InitializationBlockSummary summary) {
    return new InitializationResult(
        plans,
        blocks + 1,
        skippedBlocks,
        inserted + summary.inserted(),
        skipped + summary.skipped()
    );
  }
}
