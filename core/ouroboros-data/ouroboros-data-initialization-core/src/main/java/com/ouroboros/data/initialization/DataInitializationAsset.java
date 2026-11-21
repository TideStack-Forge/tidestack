package com.ouroboros.data.initialization;

import java.util.Objects;

public record DataInitializationAsset(
    String sourceKind,
    String sourceLocation,
    DataInitializationPlan plan
) {
  public DataInitializationAsset {
    sourceKind = requireText(sourceKind, "sourceKind");
    sourceLocation = requireText(sourceLocation, "sourceLocation");
    plan = Objects.requireNonNull(plan, "plan");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " 不能为空");
    }
    return value.trim();
  }
}
