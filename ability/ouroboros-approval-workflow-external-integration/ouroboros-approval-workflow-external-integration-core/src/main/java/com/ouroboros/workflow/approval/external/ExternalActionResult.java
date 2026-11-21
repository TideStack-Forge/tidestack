package com.ouroboros.workflow.approval.external;

/**
 * 外部动作执行结果。
 *
 * @param accepted       外部是否已接受动作（true=可确认成功或已接受）
 * @param resultStatus   外部动作后的状态（可空）
 * @param outcome        规范化结果（可空）
 * @param externalUrl    外部地址（可空）
 * @param raw            外部原始响应（可空）
 */
public record ExternalActionResult(
    boolean accepted,
    String resultStatus,
    String outcome,
    String externalUrl,
    Object raw) {
}
