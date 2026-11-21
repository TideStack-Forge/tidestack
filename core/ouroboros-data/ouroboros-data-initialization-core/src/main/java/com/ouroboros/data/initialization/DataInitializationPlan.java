package com.ouroboros.data.initialization;

import java.util.List;

public record DataInitializationPlan(
    String id,
    String version,
    List<InitializationBlock> blocks
) {
  public DataInitializationPlan {
    id = requireText(id, "id");
    version = version == null || version.isBlank() ? "default" : version.trim();
    blocks = blocks == null ? List.of() : List.copyOf(blocks);
    if (blocks.isEmpty()) {
      throw new IllegalArgumentException("初始化计划至少需要一个 block");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("初始化计划 " + name + " 不能为空");
    }
    return value.trim();
  }
}
