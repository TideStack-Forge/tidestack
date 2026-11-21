package com.ouroboros.workflow.approval.logicflow;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.workflow.approval.FlowInstanceVariables;
import com.ouroboros.workflow.approval.FlowStartCommand;
import com.ouroboros.workflow.approval.FlowStartResult;

public class StartProcessNode extends ApprovalWorkflowLogicFlowNodeSupport<String> {
  private static final String TYPE = "startProcess";

  private final String processId;

  public StartProcessNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    this.processId = meta.getExtraProp(String.class, "processId")
        .map(String::trim)
        .orElse("");
    requireMetaValue(processId, "发起流程节点未配置流程定义 ID");
  }

  @Override
  protected String doExecute(Object input) {
    Map<String, Object> request = requireObjectMap(input);
    String userId = stringValue(request, "initiator")
        .orElseGet(this::currentUserId);
    Map<String, Object> formData = toObjectMap(request.get("formData"));
    Map<String, Object> instanceVariables = instanceVariables(request);
    normalizeBusinessRecord(instanceVariables);
    return flowInstanceService().start(new FlowStartCommand(processId, userId,
            toAssigneeMap(request.get("taskAssignee")), instanceVariables, formData,
            request.get("cc") == null ? null : toObjectMap(request.get("cc")),
            stringValue(request, FlowInstanceVariables.IDEMPOTENCY_KEY).orElse(null),
            toAssigneeMap(request.get("approvers"))))
        .map(FlowStartResult::acceptedExecutionId)
        .getOrElseThrow(cause -> runtimeFailure("发起审批流程失败: " + cause.getMessage(), cause, input));
  }

  private void normalizeBusinessRecord(Map<String, Object> variables) {
    String recordType = stringValue(variables, FlowInstanceVariables.BUSINESS_RECORD_TYPE).orElse(null);
    String recordId = stringValue(variables, FlowInstanceVariables.BUSINESS_RECORD_ID).orElse(null);
    if (recordType == null && recordId == null) { return; }
    if (recordType == null || recordId == null) {
      throw runtimeFailure("发起审批流程缺少完整业务记录上下文", null, variables);
    }
    variables.put(FlowInstanceVariables.BUSINESS_RECORD_TYPE, recordType);
    variables.put(FlowInstanceVariables.BUSINESS_RECORD_ID, recordId);
  }

  private Map<String, Object> instanceVariables(Map<String, Object> request) {
    Map<String, Object> result = new LinkedHashMap<>();
    request.forEach((key, value) -> {
      if (!"initiator".equals(key) && !"formData".equals(key) && !"taskAssignee".equals(key)
          && !"callbackUrl".equals(key) && !"cc".equals(key) && !"approvers".equals(key)) {
        result.put(key, value);
      }
    });
    return result;
  }

  public static class Builder implements NodeBuilder<StartProcessNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return TYPE.equalsIgnoreCase(meta.getType());
    }

    @Override
    public StartProcessNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new StartProcessNode(owner, metadata);
    }
  }
}
