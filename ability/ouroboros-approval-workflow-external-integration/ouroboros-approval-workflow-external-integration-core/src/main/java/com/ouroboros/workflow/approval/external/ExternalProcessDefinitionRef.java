package com.ouroboros.workflow.approval.external;

/**
 * 外部流程定义引用。
 *
 * @param namespace         命名空间（如租户/企业标识，可空）
 * @param definitionId      外部定义稳定 ID（钉钉为 processCode）
 * @param definitionVersion 外部定义版本（可空；无统一版本字段的平台以 schemaHash 检测变化）
 * @param schemaHash        定义 schema 摘要（适配器校验时记录，可空）
 */
public record ExternalProcessDefinitionRef(
    String namespace,
    String definitionId,
    String definitionVersion,
    String schemaHash) {
}
