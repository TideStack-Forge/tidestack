package com.ouroboros.workflow.approval.external;

/**
 * 外部流程启动结果。
 *
 * @param externalInstanceId 外部实例 ID
 * @param externalUrl        可选外部详情访问地址（LINK 交互使用）
 * @param initialSnapshot    可选初始快照
 */
public record ExternalStartResult(
    String externalInstanceId,
    String externalUrl,
    ExternalInstanceSnapshot initialSnapshot) {
}
