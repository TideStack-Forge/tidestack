package com.ouroboros.workflow.approval.logicflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.workflow.approval.model.FlowProcessInfo;

public class AttachProcessInfoNode extends ApprovalWorkflowLogicFlowNodeSupport<Object> {
  private static final String TYPE = "attachProcessInfo";

  private final String processId;
  private final String modelPath;
  private final String businessRecordType;

  public AttachProcessInfoNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    this.processId = meta.getExtraProp(String.class, "processId")
        .map(String::trim)
        .orElse("");
    this.modelPath = meta.getExtraProp(String.class, "modelPath")
        .map(String::trim)
        .orElse("");
    this.businessRecordType = meta.getExtraProp(String.class, "businessRecordType").orElse("");
    requireMetaValue(processId, "附加工作流信息节点未配置流程定义 ID");
    requireMetaValue(modelPath, "附加工作流信息节点未配置业务模型路径");
  }

  @Override
  protected Object doExecute(Object input) {
    if (input instanceof Map<?, ?> map) {
      return attachToPagedResult(map);
    }
    if (input instanceof Iterable<?> iterable) {
      return attachToRecords(iterable);
    }
    return input;
  }

  private Map<String, Object> attachToPagedResult(Map<?, ?> input) {
    Map<String, Object> result = toObjectMap(input);
    Object items = result.get("items");
    if (items instanceof Iterable<?> iterable) {
      result.put("items", attachToRecords(iterable));
    }
    return result;
  }

  private List<Object> attachToRecords(Iterable<?> iterable) {
    List<Object> records = new ArrayList<Object>();
    List<Map<String, Object>> recordMaps = new ArrayList<Map<String, Object>>();
    List<String> approvalUrls = new ArrayList<String>();

    for (Object item : iterable) {
      if (item instanceof Map<?, ?> map) {
        Map<String, Object> record = toObjectMap(map);
        records.add(record);
        recordMaps.add(record);
        buildApprovalUrl(record).ifPresent(approvalUrls::add);
      } else {
        records.add(item);
      }
    }

    if (approvalUrls.isEmpty()) {
      return records;
    }

    Map<String, FlowProcessInfo> processInfoByApprovalUrl = getProcessInfoByApprovalUrl(approvalUrls);
    recordMaps.forEach(record -> buildApprovalUrl(record)
        .map(processInfoByApprovalUrl::get)
        .map(this::toProcessInfoMap)
        .ifPresent(processInfo -> record.put("processInfo", processInfo)));
    return records;
  }

  private Map<String, FlowProcessInfo> getProcessInfoByApprovalUrl(List<String> approvalUrls) {
    var query = businessRecordType.isBlank()
        ? flowInstanceService().getProcessInfoByApprovalUrls(processId, approvalUrls)
        : flowInstanceService().getProcessInfoByApprovalUrls(processId, approvalUrls, businessRecordType);
    return query.getOrElseThrow(e -> runtimeFailure("附加工作流信息失败: " + e.getMessage(), e, approvalUrls));
  }

  private Optional<String> buildApprovalUrl(Map<String, Object> record) {
    return stringValue(record, "id")
        .map(id -> normalizedModelPath() + id + "/approval");
  }

  private String normalizedModelPath() {
    return modelPath.endsWith("/") ? modelPath : modelPath + "/";
  }

  private Map<String, Object> toProcessInfoMap(FlowProcessInfo info) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("instanceId", info.getInstanceId());
    result.put("flowKey", info.getFlowKey());
    result.put("status", info.getStatus());
    result.put("outcome", info.getOutcome());
    result.put("starter", info.getStarter());
    result.put("startTime", info.getStartTime());
    result.put("endTime", info.getEndTime());
    result.put("processable", info.isProcessable());
    result.put("processableTaskId", info.getProcessableTaskId());
    result.put("externalUrl", info.getExternalUrl());
    result.put("diagramAvailable", info.isDiagramAvailable());
    result.put("timelineAvailable", info.isTimelineAvailable());
    result.put("cancellable", info.isCancellable());
    result.put("tasks", Optional.ofNullable(info.getTasks())
        .orElseGet(Collections::emptyList)
        .stream()
        .map(this::toTaskInfoMap)
        .collect(Collectors.toList()));
    return result;
  }

  private Map<String, Object> toTaskInfoMap(FlowProcessInfo.TaskInfo task) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("taskId", task.getTaskId());
    result.put("taskName", task.getTaskName());
    result.put("taskDefinitionKey", task.getTaskDefinitionKey());
    result.put("nodeType", task.getNodeType());
    result.put("assignee", task.getAssignee());
    result.put("startTime", task.getStartTime());
    result.put("active", task.isActive());
    return result;
  }

  public static class Builder implements NodeBuilder<AttachProcessInfoNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return TYPE.equalsIgnoreCase(meta.getType());
    }

    @Override
    public AttachProcessInfoNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new AttachProcessInfoNode(owner, metadata);
    }
  }
}
