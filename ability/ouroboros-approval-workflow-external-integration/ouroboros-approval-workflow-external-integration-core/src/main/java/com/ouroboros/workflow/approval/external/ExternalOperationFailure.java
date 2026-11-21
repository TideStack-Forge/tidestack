package com.ouroboros.workflow.approval.external;

import java.util.Objects;

/**
 * 带远程副作用确定性的 provider-neutral 操作失败。
 *
 * <p>该异常只承载跨端口必须保留的语义；provider 原始异常作为 cause 保留用于诊断。
 */
public final class ExternalOperationFailure extends RuntimeException {

  private final ExternalEffectCertainty certainty;

  public ExternalOperationFailure(
      ExternalEffectCertainty certainty, String message, Throwable cause) {
    super(message, cause);
    this.certainty = Objects.requireNonNull(certainty, "certainty");
  }

  public ExternalEffectCertainty certainty() {
    return certainty;
  }

  public static ExternalOperationFailure unknown(String message, Throwable cause) {
    return new ExternalOperationFailure(ExternalEffectCertainty.UNKNOWN, message, cause);
  }

  public static ExternalOperationFailure definitelyNotApplied(String message, Throwable cause) {
    return new ExternalOperationFailure(
        ExternalEffectCertainty.DEFINITELY_NOT_APPLIED, message, cause);
  }

  public static ExternalOperationFailure applied(String message, Throwable cause) {
    return new ExternalOperationFailure(ExternalEffectCertainty.APPLIED, message, cause);
  }
}
