package com.ouroboros.data.initialization;

public record LedgerKey(
    String planId,
    String blockId,
    String targetModel,
    String assetVersion,
    String contentHash
) {
  public LedgerKey {
    planId = requireText(planId, "planId");
    blockId = requireText(blockId, "blockId");
    targetModel = requireText(targetModel, "targetModel");
    assetVersion = requireText(assetVersion, "assetVersion");
    contentHash = requireText(contentHash, "contentHash");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("账本键 " + name + " 不能为空");
    }
    return value.trim();
  }
}
