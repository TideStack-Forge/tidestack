package com.ouroboros.workflow.approval.external.runtime.start;

import java.util.List;
import java.util.Map;

import com.ouroboros.workflow.approval.external.CcTarget;
import com.ouroboros.workflow.approval.FlowStartCommand;
import com.ouroboros.workflow.approval.FlowInstanceVariables;

/**
 * 结果型启动命令。
 *
 * @param flowKey            流程标识
 * @param businessRecordType 业务记录类型（业务模型全名；映射配置了 resultBindingJson 时可被覆盖）
 * @param businessRecordId   业务记录主键（映射配置了 businessRecordIdPath 时从表单提取覆盖）
 * @param userId             发起人（平台用户 ID）
 * @param formData           表单数据
 * @param idempotencyKey     幂等键（同一适配器/连接下重复发起返回既有结果）
 * @param cc                 可选抄送人（外部定义允许时透传）
 * @param approvers          可选发起时覆盖固定审批人
 * @param targetSelectActioners 可选发起人自选节点人选
 */
public record ResultOnlyStartCommand(
    String flowKey,
    String businessRecordType,
    String businessRecordId,
    String userId,
    Map<String, Object> formData,
    String idempotencyKey,
    List<CcTarget> cc,
    Map<String, List<String>> approvers,
    Map<String, List<String>> targetSelectActioners,
    Map<String, Object> instanceVariables) {

  public ResultOnlyStartCommand(String flowKey, String businessRecordType, String businessRecordId,
      String userId, Map<String, Object> formData, String idempotencyKey, List<CcTarget> cc,
      Map<String, List<String>> approvers, Map<String, List<String>> targetSelectActioners) {
    this(flowKey, businessRecordType, businessRecordId, userId, formData, idempotencyKey,
        cc, approvers, targetSelectActioners, null);
  }

  public FlowStartCommand toFlowStartCommand() {
    Map<String, Object> variables = new java.util.LinkedHashMap<>(instanceVariables == null ? Map.of() : instanceVariables);
    if (businessRecordType != null) { variables.put(FlowInstanceVariables.BUSINESS_RECORD_TYPE, businessRecordType); }
    if (businessRecordId != null) { variables.put(FlowInstanceVariables.BUSINESS_RECORD_ID, businessRecordId); }
    Map<String, Object> copy = cc == null ? null : Map.of("targets", cc.stream().map(target -> {
      Map<String, Object> value = new java.util.LinkedHashMap<>();
      value.put("targetType", target.targetType());
      value.put("targetId", target.targetId());
      value.put("position", target.position());
      return value;
    }).toList());
    return new FlowStartCommand(flowKey, userId, targetSelectActioners, variables, formData, copy, idempotencyKey, approvers);
  }

}
