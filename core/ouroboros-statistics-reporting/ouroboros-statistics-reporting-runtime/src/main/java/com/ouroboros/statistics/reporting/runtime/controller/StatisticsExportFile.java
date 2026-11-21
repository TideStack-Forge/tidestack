package com.ouroboros.statistics.reporting.runtime.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.FileResult;
import com.ouroboros.util.excel.ExcelWriter;

/** Builds a complete export before the standard platform file response starts. */
final class StatisticsExportFile {
  private StatisticsExportFile() { }

  static FileResult create(String name, Map<String, Object> payload) throws IOException {
    List<String> names = new ArrayList<>();
    List<String> titles = new ArrayList<>();
    if (payload.get("columns") instanceof List<?> columns) {
      for (Object value : columns) {
        if (value instanceof Map<?, ?> column) {
          String field = String.valueOf(column.get("name"));
          names.add(field);
          titles.add(column.get("title") == null ? field : column.get("title").toString());
        }
      }
    }
    if (names.isEmpty()) throw new BusinessException("没有可导出的字段");
    List<List<Object>> rows = new ArrayList<>();
    if (payload.get("rows") instanceof List<?> values) {
      for (Object value : values) {
        if (value instanceof Map<?, ?> row) rows.add(names.stream().<Object>map(row::get).toList());
      }
    }
    var path = Files.createTempFile("ouroboros-statistics-export-", ".xlsx");
    try {
      try (var output = Files.newOutputStream(path)) {
        ExcelWriter.writeExcel(output, new ExcelWriter.SheetData("查询结果", titles, rows).setFreezeRowCount(1), false);
      }
      return FileResult.temporary(path, name + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    } catch (IOException | RuntimeException error) {
      try { Files.deleteIfExists(path); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
      throw error;
    }
  }
}
