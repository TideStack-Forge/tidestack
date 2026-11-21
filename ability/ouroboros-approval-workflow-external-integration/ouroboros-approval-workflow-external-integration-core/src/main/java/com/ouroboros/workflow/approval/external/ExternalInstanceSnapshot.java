package com.ouroboros.workflow.approval.external;

import java.util.Map;

/**
 * 外部实例快照。
 *
 * @param externalInstanceId 外部实例 ID
 * @param lifecycleStatus    外部实例生命周期状态（RUNNING/SUSPENDED/FINISHED/CANCELED）
 * @param resultCode         规范结果状态（APPROVED/REJECTED/CANCELED/ERROR，可空）
 * @param outcome            业务结果（approved/rejected/cancelled/failed 等，可空）
 * @param externalUrl        外部详情地址（可空）
 * @param raw                外部原始字段（适配器透传，避免丢失信息）
 */
public record ExternalInstanceSnapshot(
    String externalInstanceId,
    String lifecycleStatus,
    String resultCode,
    String outcome,
    String externalUrl,
    Map<String, Object> raw) {
}
