package com.ouroboros.workflow.approval.external;

import java.util.List;
import java.util.Map;

/**
 * 外部流程启动命令（规范契约，不随外部平台变化）。
 *
 * <p>本命令中出现的用户标识一律是<b>平台规范用户 ID</b>（{@code userIdentity} 语义）。
 * 适配器在 ACL 内通过 {@link ExternalUserIdentityMapper} 把规范 ID 翻译为外部引擎用户标识后
 * 才可调用外部 API，禁止原样透传；找不到映射时返回失败（{@code EXTERNAL_USER_MAPPING_MISSING}）。
 *
 * @param originatorUserId      启动人（平台规范用户 ID）
 * @param externalDefinitionRef 外部流程定义引用
 * @param businessRecord        业务记录定位（结果型绑定使用）
 * @param formData              表单数据
 * @param variables             自定义流程变量
 * @param idempotencyKey        幂等键
 * @param cc                    可选抄送人（平台规范用户 ID，仅当外部定义允许时透传）
 * @param approvers             可选发起时覆盖固定审批人（平台规范用户 ID，仅当外部定义允许覆盖时透传系统候选人解析结果）
 * @param targetSelectActioners 可选发起人自选节点人选（节点 ID → 平台规范用户 ID 列表）
 */
public record ExternalStartCommand(
    String originatorUserId,
    ExternalProcessDefinitionRef externalDefinitionRef,
    ExternalBusinessRecord businessRecord,
    Map<String, Object> formData,
    Map<String, Object> variables,
    String idempotencyKey,
    List<CcTarget> cc,
    Map<String, List<String>> approvers,
    Map<String, List<String>> targetSelectActioners) {

  /**
   * 业务记录定位。
   *
   * @param recordType 业务记录类型（业务模型全名）
   * @param recordId   业务记录主键
   */
  public record ExternalBusinessRecord(String recordType, String recordId) {
  }
}
