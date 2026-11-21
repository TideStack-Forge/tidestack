package com.ouroboros.workflow.approval.external;

/**
 * 规范化外部事件包（inbox 幂等键与顺序处理的输入）。
 *
 * @param eventId            事件唯一 ID（幂等键）
 * @param adapterId          适配器 ID
 * @param connectionId       连接 ID
 * @param externalInstanceId 外部实例 ID
 * @param externalTaskId     外部任务 ID（可空）
 * @param sequence           外部事件序号（可空，不假设必然递增）
 * @param externalEventTime  外部事件时间（适配器定义格式，可空）
 * @param eventType          外部事件类型（原始语义，由结果映射解释）
 * @param resultCode         外部结果码（可空）
 * @param payloadDigest      原始载荷摘要（可空，审计用）
 */
public record ExternalCallbackEnvelope(
    String eventId,
    String adapterId,
    String connectionId,
    String externalInstanceId,
    String externalTaskId,
    String sequence,
    String externalEventTime,
    String eventType,
    String resultCode,
    String payloadDigest) {
}
