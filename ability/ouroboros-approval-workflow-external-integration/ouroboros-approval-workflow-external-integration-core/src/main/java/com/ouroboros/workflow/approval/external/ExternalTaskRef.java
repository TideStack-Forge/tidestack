package com.ouroboros.workflow.approval.external;

/**
 * 外部任务引用。
 *
 * @param externalTaskId     外部任务 ID
 * @param externalInstanceId 外部实例 ID
 * @param connectionId       连接 ID
 */
public record ExternalTaskRef(String externalTaskId, String externalInstanceId, String connectionId) {
}
