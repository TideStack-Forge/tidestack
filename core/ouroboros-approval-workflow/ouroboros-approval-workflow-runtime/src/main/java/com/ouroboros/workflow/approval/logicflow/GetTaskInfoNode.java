package com.ouroboros.workflow.approval.logicflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;

public class GetTaskInfoNode extends ApprovalWorkflowLogicFlowNodeSupport<Map<String, Object>> {
  private static final String TYPE = "getTaskInfo";

  public GetTaskInfoNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  @Override
  protected Map<String, Object> doExecute(Object input) {
    Map<String, Object> request = requireObjectMap(input);
    String taskId = stringValue(request, "taskId")
        .orElseThrow(() -> new FlowRuntimeException("获取审批任务信息缺少 taskId", this, java.util.Collections.singletonList(input)));
    FlowTaskDetail detail = flowTaskService().getTaskDetail(taskId);
    if (detail == null) {
      throw new FlowRuntimeException("未找到审批任务: " + taskId, this, java.util.Collections.singletonList(input));
    }
    return toResult(detail);
  }

  private Map<String, Object> toResult(FlowTaskDetail detail) {
    Map<String, Object> taskInfo = toTaskInfo(detail);
    Map<String, Object> result = new LinkedHashMap<>(taskInfo);
    result.put("taskInfo", taskInfo);
    return result;
  }

  private Map<String, Object> toTaskInfo(FlowTaskDetail detail) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("taskId", detail.getId());
    result.put("taskName", detail.getName());
    result.put("instanceId", detail.getInstanceId());
    result.put("flowKey", detail.getFlowKey());
    result.put("flowName", detail.getFlowName());
    result.put("taskDefinitionKey", detail.getTaskDefinitionKey());
    result.put("type", detail.getTaskDefinitionKey());
    result.put("startTime", detail.getStartTime());
    result.put("endTime", detail.getEndTime());
    result.put("assignee", detail.getAssignee());
    result.put("candidateUsers", detail.getCandidateUsers());
    result.put("processStarter", detail.getStarter());
    result.put("fields", detail.getFields());
    result.put("actions", toLegacyActionTokens(detail.getActions()));
    result.put("actionNames", detail.getActions());
    result.put("active", detail.isActive());
    return result;
  }

  private List<String> toLegacyActionTokens(List<String> actions) {
    List<String> result = new ArrayList<>();
    Optional.ofNullable(actions)
        .orElseGet(Collections::emptyList)
        .forEach(action -> addLegacyActionToken(result, action));
    return result;
  }

  private void addLegacyActionToken(List<String> result, String action) {
    if (action == null || action.trim().isEmpty()) {
      return;
    }

    var normalized = action.trim();
    switch (normalized.toLowerCase(Locale.ROOT)) {
      case "approve", "approved", "pass" -> addLegacyActionTokens(result, "PASS");
      case "reject", "rejected", "veto" -> addLegacyActionTokens(result, "REJECT", "VETO");
      case "transfer" -> addLegacyActionTokens(result, "TRANSFER");
      case "addsign", "sign" -> addLegacyActionTokens(result, "SIGN");
      case "freereject", "free_reject", "f_reject" -> addLegacyActionTokens(result, "F_REJECT");
      case "freerecover", "free_recover", "f_recover" -> addLegacyActionTokens(result, "F_RECOVER");
      case "freeapprove", "freeapproval", "free_pass", "f_pass" -> addLegacyActionTokens(result, "F_PASS");
      case "complete", "done" -> addLegacyActionTokens(result, "COMPLETE");
      case "rollback" -> addLegacyActionTokens(result, "ROLLBACK");
      default -> addLegacyActionTokens(result, normalized.toUpperCase(Locale.ROOT));
    }
  }

  private void addLegacyActionTokens(List<String> result, String... tokens) {
    for (String token : tokens) {
      if (token != null && !result.contains(token)) {
        result.add(token);
      }
    }
  }

  public static class Builder implements NodeBuilder<GetTaskInfoNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return TYPE.equalsIgnoreCase(meta.getType());
    }

    @Override
    public GetTaskInfoNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new GetTaskInfoNode(owner, metadata);
    }
  }
}
