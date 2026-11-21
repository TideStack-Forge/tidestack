package com.ouroboros.businessmodel.logicflow.childcollection;

public enum ChildCollectionSyncMode {
  CREATE,
  REPLACE;

  public static ChildCollectionSyncMode from(String value) {
    for (var mode : values()) {
      if (mode.name().equalsIgnoreCase(value)) {
        return mode;
      }
    }
    throw new IllegalArgumentException("不支持的子表同步模式: " + value);
  }
}
