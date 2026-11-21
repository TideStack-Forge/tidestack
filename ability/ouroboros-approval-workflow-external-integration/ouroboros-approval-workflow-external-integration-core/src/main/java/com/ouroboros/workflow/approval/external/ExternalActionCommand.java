package com.ouroboros.workflow.approval.external;

import java.util.Map;

/**
 * 外部动作命令。
 *
 * @param canonicalAction   平台规范动作名（approve/reject/rollback/transfer/addSign/remind 等）
 * @param externalAction    映射后的外部动作名（传给外部适配器执行）
 * @param userId            触发动作的平台用户 ID
 * @param comment           审批意见
 * @param formData          表单数据
 * @param payload           通用载荷
 * @param idempotencyKey    幂等键
 * @param mappingVersion    映射版本（动作结果按该版本解释）
 * @param externalInstanceRef 外部实例引用
 * @param externalTaskRef   外部任务引用（任务级动作时非空）
 */
public record ExternalActionCommand(
    String canonicalAction,
    String externalAction,
    String userId,
    String comment,
    Map<String, Object> formData,
    Map<String, Object> payload,
    String idempotencyKey,
    Integer mappingVersion,
    ExternalInstanceRef externalInstanceRef,
    ExternalTaskRef externalTaskRef) {
}
