package com.ouroboros.workflow.approval.external;

/**
 * 外部实例引用。
 *
 * @param externalInstanceId 外部实例 ID
 * @param connectionId       连接 ID
 */
public record ExternalInstanceRef(String externalInstanceId, String connectionId) {
}
