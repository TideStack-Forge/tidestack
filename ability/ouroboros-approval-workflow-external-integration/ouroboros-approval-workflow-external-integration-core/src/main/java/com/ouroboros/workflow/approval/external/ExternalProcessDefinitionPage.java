package com.ouroboros.workflow.approval.external;

import java.util.List;

/**
 * 外部流程定义分页。
 *
 * @param definitions 定义候选项
 * @param hasMore     是否还有下一页
 */
public record ExternalProcessDefinitionPage(List<ExternalProcessDefinition> definitions, boolean hasMore) {
}
