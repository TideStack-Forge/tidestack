package com.ouroboros.businessmodel.action;

import java.util.Map;

/** Adapts a dynamic wire object without collapsing partition presence. */
public final class BusinessActionInputs {
  private BusinessActionInputs() {
  }

  public static BusinessActionInput<?, ?> fromPartitions(Map<String, Object> partitions) {
    if (partitions.keySet().stream().anyMatch(key -> !"params".equals(key) && !"data".equals(key))) {
      throw new com.ouroboros.exception.BusinessException("Action 输入仅支持 params/data 分区");
    }
    return new DefaultBusinessActionInput<>(partitions.containsKey("params"), partitions.get("params"),
        partitions.containsKey("data"), partitions.get("data"));
  }
}
