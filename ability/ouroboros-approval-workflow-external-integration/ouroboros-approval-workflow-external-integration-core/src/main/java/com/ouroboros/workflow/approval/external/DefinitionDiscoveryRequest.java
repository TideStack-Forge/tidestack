package com.ouroboros.workflow.approval.external;

/**
 * 外部流程定义发现请求。
 *
 * @param connectionId 连接 ID
 * @param keyword      可选搜索关键词
 */
public record DefinitionDiscoveryRequest(String connectionId, String keyword) {
}
