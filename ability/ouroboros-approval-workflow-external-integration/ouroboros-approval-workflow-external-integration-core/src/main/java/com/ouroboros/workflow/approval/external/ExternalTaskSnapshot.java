package com.ouroboros.workflow.approval.external;

import java.util.List;
import java.util.Map;

/**
 * 外部任务快照。
 *
 * @param externalTaskId 外部任务 ID
 * @param nodeId         节点标识（可空）
 * @param assignee       处理人（外部用户 ID，可空）
 * @param status         任务状态
 * @param externalUrl    外部任务地址（可空）
 * @param raw            外部原始字段
 */
public record ExternalTaskSnapshot(
    String externalTaskId,
    String nodeId,
    String assignee,
    String status,
    String externalUrl,
    Map<String, Object> raw) {
}
