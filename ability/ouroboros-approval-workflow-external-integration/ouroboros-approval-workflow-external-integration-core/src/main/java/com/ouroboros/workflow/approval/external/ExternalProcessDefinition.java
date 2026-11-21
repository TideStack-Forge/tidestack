package com.ouroboros.workflow.approval.external;

/**
 * 外部流程定义候选项。
 *
 * @param definitionId      定义稳定 ID
 * @param definitionVersion 版本（可空）
 * @param name              显示名称
 * @param schemaHash        schema 摘要（可空）
 */
public record ExternalProcessDefinition(
    String definitionId,
    String definitionVersion,
    String name,
    String schemaHash) {
}
