package com.ouroboros.data.initialization;

import java.util.Objects;

public record InitializationRun(String id, LedgerKey key) {
  public InitializationRun {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("初始化运行记录 id 不能为空");
    }
    key = Objects.requireNonNull(key, "key");
  }
}
