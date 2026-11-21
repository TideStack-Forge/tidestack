package com.ouroboros.workflow.approval.external;

import io.vavr.control.Try;

/**
 * 外部启动结果恢复端口。
 *
 * <p>适配器只有在能够使用稳定请求身份可靠查询远端结果时才实现该端口。未实现时，
 * runtime 必须把启动事实保留为 {@code START_UNKNOWN}，禁止自动再次创建远端实例。
 */
public interface ExternalWorkflowStartRecovery {

  /**
   * 按稳定请求身份查询一次外部启动结果。
   *
   * <p>适配器不得把“查询接口暂时不可用”返回为 {@link ExternalStartRecoveryResult.Resolution#NOT_FOUND}；
   * 这种情况必须返回 {@code UNKNOWN} 或带 {@link ExternalEffectCertainty#UNKNOWN} 的失败。
   */
  Try<ExternalStartRecoveryResult> recover(ExternalStartRecoveryQuery query);
}
