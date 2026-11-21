package com.ouroboros.workflow.approval.external;

import java.util.Objects;

/** 外部启动结果查询的明确三态结果。 */
public record ExternalStartRecoveryResult(
    Resolution resolution,
    ExternalStartResult startResult,
    String diagnostic) {

  public enum Resolution {
    /** 已定位到与查询身份匹配的远端实例。 */
    FOUND,
    /** 适配器已确认该稳定请求身份没有远端实例，可以安全重试。 */
    NOT_FOUND,
    /** 无法证明远端是否已创建实例，禁止自动重试。 */
    UNKNOWN
  }

  public ExternalStartRecoveryResult {
    Objects.requireNonNull(resolution, "resolution");
    if (resolution == Resolution.FOUND
        && (startResult == null || startResult.externalInstanceId() == null
            || startResult.externalInstanceId().isBlank())) {
      throw new IllegalArgumentException("FOUND 恢复结果必须包含 externalInstanceId");
    }
    if (resolution != Resolution.FOUND && startResult != null) {
      throw new IllegalArgumentException("非 FOUND 恢复结果不能携带 startResult");
    }
  }

  public static ExternalStartRecoveryResult found(ExternalStartResult result) {
    return new ExternalStartRecoveryResult(Resolution.FOUND, result, null);
  }

  public static ExternalStartRecoveryResult notFound(String diagnostic) {
    return new ExternalStartRecoveryResult(Resolution.NOT_FOUND, null, diagnostic);
  }

  public static ExternalStartRecoveryResult unknown(String diagnostic) {
    return new ExternalStartRecoveryResult(Resolution.UNKNOWN, null, diagnostic);
  }
}
