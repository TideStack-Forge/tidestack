package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.Map;
import java.util.Set;
import com.ouroboros.exception.BusinessException;

/** One platform policy observation. Dataset values can only reduce these limits. */
public record DatasetExecutionPolicy(int queryTimeoutSeconds, int maxPageSize, int maxExportRows, String revision) {
  public static final String KEY = "statisticsReporting.executionPolicy";
  private static final Set<String> FIELDS = Set.of("queryTimeoutSeconds", "maxPageSize", "maxExportRows");

  public DatasetExecutionPolicy {
    if (queryTimeoutSeconds < 1 || maxPageSize < 1 || maxExportRows < 1) {
      throw invalid("限制必须为正整数");
    }
  }

  public static DatasetExecutionPolicy defaults() {
    return new DatasetExecutionPolicy(30, 1000, 100000, "default");
  }

  public static DatasetExecutionPolicy parse(Object value, String revision) {
    if (!(value instanceof Map<?, ?> values) || !values.keySet().equals(FIELDS)) {
      throw invalid("平台策略必须同时包含查询超时、最大分页和最大导出行数");
    }
    return new DatasetExecutionPolicy(number(values.get("queryTimeoutSeconds")), number(values.get("maxPageSize")),
        number(values.get("maxExportRows")), revision);
  }

  public DatasetExecutionPolicy tighten(Map<String, Object> values, boolean saving) {
    if (values == null || values.isEmpty()) return this;
    if (!FIELDS.containsAll(values.keySet())) throw invalid("存在未知限制项");
    return new DatasetExecutionPolicy(limit(values, "queryTimeoutSeconds", queryTimeoutSeconds, saving),
        limit(values, "maxPageSize", maxPageSize, saving), limit(values, "maxExportRows", maxExportRows, saving), revision);
  }

  private static int limit(Map<String, Object> values, String name, int platform, boolean saving) {
    Object value = values.get(name);
    if (value == null || "".equals(value)) return platform;
    int limit = number(value);
    if (saving && limit > platform) throw invalid(name + " 不能超过平台上限 " + platform);
    return Math.min(limit, platform);
  }

  private static int number(Object value) {
    if (!(value instanceof Number number)) throw invalid("限制必须为正整数");
    double numeric = number.doubleValue();
    if (!Double.isFinite(numeric) || numeric < 1 || numeric > Integer.MAX_VALUE || numeric != Math.rint(numeric)) {
      throw invalid("限制必须为正整数");
    }
    return number.intValue();
  }

  private static BusinessException invalid(String message) {
    return new BusinessException("dataset.execution-policy-invalid: " + message);
  }
}
