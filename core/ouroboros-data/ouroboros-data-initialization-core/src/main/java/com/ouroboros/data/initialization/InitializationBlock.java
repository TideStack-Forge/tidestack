package com.ouroboros.data.initialization;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public record InitializationBlock(
    String id,
    String targetModel,
    String version,
    List<String> matchFields,
    List<Map<String, Object>> records
) {
  public InitializationBlock {
    id = requireText(id, "id");
    targetModel = requireText(targetModel, "targetModel");
    version = version == null || version.isBlank() ? null : version.trim();
    matchFields = normalizeMatchFields(matchFields);
    records = normalizeRecords(records);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("初始化 block " + name + " 不能为空");
    }
    return value.trim();
  }

  private static List<String> normalizeMatchFields(List<String> fields) {
    if (fields == null || fields.isEmpty()) {
      throw new IllegalArgumentException("初始化 block matchFields 不能为空");
    }
    return fields.stream()
        .map(field -> requireText(field, "matchFields"))
        .distinct()
        .toList();
  }

  private static List<Map<String, Object>> normalizeRecords(List<Map<String, Object>> records) {
    if (records == null || records.isEmpty()) {
      return List.of();
    }
    return records.stream()
        .map(record -> Collections.unmodifiableMap(new LinkedHashMap<>(record)))
        .toList();
  }
}
