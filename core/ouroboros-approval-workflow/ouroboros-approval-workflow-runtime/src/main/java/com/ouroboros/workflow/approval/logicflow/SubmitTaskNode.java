package com.ouroboros.workflow.approval.logicflow;

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

public class SubmitTaskNode extends ApprovalWorkflowLogicFlowNodeSupport<Object> {
  private static final String TYPE = "submitTask";

  public SubmitTaskNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  @Override
  protected Object doExecute(Object input) {
    Map<String, Object> request = requireObjectMap(input);
    String taskId = stringValue(request, "taskId")
        .orElseThrow(() -> new FlowRuntimeException("提交审批任务缺少 taskId", this, java.util.Collections.singletonList(input)));
    String action = stringValue(request, "action")
        .map(this::normalizeAction)
        .orElseThrow(() -> new FlowRuntimeException("提交审批任务缺少 action", this, java.util.Collections.singletonList(input)));
    String comment = stringValue(request, "comment").orElse(null);
    Map<String, Object> formData = toObjectMap(request.get("formData"));
    Object payload = Optional.ofNullable(request.get("payload"))
        .orElseGet(() -> resolveLegacyPayload(action, request));

    flowTaskService()
        .doAction(taskId, action, currentUserId(), comment, formData, payload)
        .getOrElseThrow(cause -> runtimeFailure("提交审批任务失败: " + cause.getMessage(), cause, input));
    return null;
  }

  private String normalizeAction(String action) {
    return switch (action.toUpperCase(Locale.ROOT)) {
      case "PASS", "APPROVE", "APPROVED" -> "approve";
      case "VETO", "REJECT", "REJECTED" -> "reject";
      case "ROLLBACK" -> "rollback";
      case "COMPLETE", "DONE" -> "complete";
      case "TRANSFER" -> "transfer";
      case "SIGN", "ADD_SIGN", "ADDSIGN" -> "addSign";
      case "F_PASS", "FREE_PASS", "FREE_APPROVE", "FREEAPPROVE", "FREEAPPROVAL" -> "freeApprove";
      case "F_REJECT", "FREE_REJECT", "FREEREJECT" -> "freeReject";
      case "F_RECOVER", "FREE_RECOVER", "FREERECOVER", "RECOVERY" -> "freeRecover";
      default -> action;
    };
  }

  private Object resolveLegacyPayload(String action, Map<String, Object> request) {
    Map<String, Object> payload = switch (action) {
      case "transfer" -> singleValuePayload("targetUserId", firstValue(request, "targetUserId", "assignUserId"));
      case "addSign" -> addSignPayload(request);
      case "freeReject", "freeRecover" -> singleValuePayload("targetNodeId", firstValue(request, "targetNodeId", "targetTaskKey"));
      case "freeApprove" -> freeApprovalPayload(request);
      default -> Collections.emptyMap();
    };
    return payload.isEmpty() ? null : payload;
  }

  private Map<String, Object> singleValuePayload(String key, Object value) {
    if (value == null) {
      return Collections.emptyMap();
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(key, value);
    return payload;
  }

  private Map<String, Object> addSignPayload(Map<String, Object> request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    Object targetUsers = firstValue(request, "targetUserIds", "assignUserIds", "assignUserId");
    if (targetUsers != null) {
      payload.put("targetUserIds", toList(targetUsers));
    }
    Object position = firstValue(request, "position", "signType");
    if (position != null) {
      payload.put("position", normalizeAddSignPosition(position));
    }
    return payload;
  }

  private Map<String, Object> freeApprovalPayload(Map<String, Object> request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    Object consultUsers = firstValue(request, "consultUserIds", "targetUserIds", "assignUserIds", "assignUserId");
    if (consultUsers == null && request.get("newTask") instanceof Map<?, ?> newTask) {
      consultUsers = firstValue(newTask, "consultUserIds", "targetUserIds", "assignUserIds", "assignUserId", "userId");
    }
    if (consultUsers != null) {
      payload.put("consultUserIds", toList(consultUsers));
    }
    return payload;
  }

  private Object firstValue(Map<?, ?> source, String... keys) {
    for (String key : keys) {
      Object value = source.get(key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private List<?> toList(Object value) {
    if (value instanceof List<?>) {
      return (List<?>) value;
    }
    return Collections.singletonList(value);
  }

  private String normalizeAddSignPosition(Object value) {
    String position = String.valueOf(value).trim();
    String compact = position.replace("-", "_").toUpperCase(Locale.ROOT);
    if ("BEFORE".equals(compact) || "BEFORECURRENT".equals(compact) || "BEFORE_CURRENT".equals(compact)) {
      return "BEFORE_CURRENT";
    }
    if ("AFTER".equals(compact) || "AFTERCURRENT".equals(compact) || "AFTER_CURRENT".equals(compact)) {
      return "AFTER_CURRENT";
    }
    return compact;
  }

  public static class Builder implements NodeBuilder<SubmitTaskNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return TYPE.equalsIgnoreCase(meta.getType());
    }

    @Override
    public SubmitTaskNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new SubmitTaskNode(owner, metadata);
    }
  }
}
