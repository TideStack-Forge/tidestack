package com.ouroboros.workflow.approval.external.fake;

import java.util.List;
import java.util.Map;
import java.util.Set;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.enterprise.EnterpriseConnectionProvider;
import com.ouroboros.workflow.approval.external.InMemoryExternalEngineCore;
import static com.ouroboros.workflow.approval.external.fake.FakeEngineAdapterProvider.*;

/** Provider-owned non-sensitive connection contract, independent of workflow capabilities. */
public final class FakeConnectionProvider implements EnterpriseConnectionProvider {
  @Override
  public String provider() { return "fake-external-engine"; }

  @Override
  public Set<String> connectionConfigFields() {
    return Set.of(START_FAIL_FIELD, AUTO_APPROVE_AFTER_SECONDS_FIELD, INITIAL_STATUS_FIELD);
  }

  @Override
  public Map<String, Object> connectionSchema() {
    return Map.of(
        "provider", provider(),
        "fields", List.of(
            Map.of(
                "name", START_FAIL_FIELD,
                "type", "boolean",
                "label", "启动失败",
                "required", false,
                "defaultValue", false,
                "description", "演示启动失败路径"),
            Map.of(
                "name", AUTO_APPROVE_AFTER_SECONDS_FIELD,
                "type", "integer",
                "label", "自动完成延迟（秒）",
                "required", false,
                "defaultValue", 0,
                "minimum", 0,
                "description", "大于 0 时，实例经过该时长后查询为 FINISHED"),
            Map.of(
                "name", INITIAL_STATUS_FIELD,
                "type", "string",
                "label", "初始状态",
                "required", false,
                "defaultValue", InMemoryExternalEngineCore.STATUS_RUNNING,
                "options", List.of(
                    Map.of("label", InMemoryExternalEngineCore.STATUS_RUNNING,
                        "value", InMemoryExternalEngineCore.STATUS_RUNNING),
                    Map.of("label", InMemoryExternalEngineCore.STATUS_FINISHED,
                        "value", InMemoryExternalEngineCore.STATUS_FINISHED)),
                "description", "演示实例启动时的生命周期状态")));
  }

  @Override
  public void validateConnectionConfig(Map<String, Object> config) {
    if (config == null) {
      return;
    }
    Object startFail = config.get(START_FAIL_FIELD);
    if (startFail != null && !(startFail instanceof Boolean)) {
      throw invalid(START_FAIL_FIELD, "必须是布尔值");
    }
    Object delay = config.get(AUTO_APPROVE_AFTER_SECONDS_FIELD);
    if (delay != null) {
      long seconds = integer(delay, AUTO_APPROVE_AFTER_SECONDS_FIELD);
      if (seconds < 0) {
        throw invalid(AUTO_APPROVE_AFTER_SECONDS_FIELD, "不能小于 0");
      }
    }
    Object initialStatus = config.get(INITIAL_STATUS_FIELD);
    if (initialStatus != null
        && !(InMemoryExternalEngineCore.STATUS_RUNNING.equals(initialStatus)
            || InMemoryExternalEngineCore.STATUS_FINISHED.equals(initialStatus))) {
      throw invalid(INITIAL_STATUS_FIELD, "只允许 RUNNING 或 FINISHED");
    }
  }

  private static long integer(Object value, String field) {
    if (value instanceof Byte || value instanceof Short || value instanceof Integer
        || value instanceof Long) {
      return ((Number) value).longValue();
    }
    throw invalid(field, "必须是整数");
  }

  private static BusinessException invalid(String field, String reason) {
    return new BusinessException("[" + "ENTERPRISE_CONNECTION_UNAVAILABLE"
        + "] fake 连接字段 " + field + " 非法: " + reason);
  }
}
