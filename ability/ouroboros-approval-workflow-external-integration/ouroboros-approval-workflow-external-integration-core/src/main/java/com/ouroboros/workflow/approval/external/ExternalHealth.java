package com.ouroboros.workflow.approval.external;

/**
 * 连接健康状态。
 *
 * @param healthy  是否健康
 * @param message  健康检查结果描述（可空）
 */
public record ExternalHealth(boolean healthy, String message) {
}
