package com.ouroboros.workflow.approval.external.runtime.start;

/**
 * 结果型启动结果。
 *
 * @param trackingId          本地跟踪 ID（STARTED 后存在）
 * @param externalInstanceId  外部实例 ID（远程已返回后存在）
 * @param externalUrl         外部详情地址（可空）
 * @param status              启动事实状态：STARTING/STARTED/START_UNKNOWN/FAILED
 * @param startFactId         本地启动事实 ID，用于未知状态恢复
 */
public record ResultOnlyStartResult(
    String trackingId,
    String externalInstanceId,
    String externalUrl,
    String status,
    String startFactId) {
}
