package com.ouroboros.workflow.approval.web.service;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.ouroboros.util.Json;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.graph.StructuredFlowDefinition;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.definition.v2.FlowEdgeV2;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;
import com.ouroboros.workflow.approval.web.vo.TargetItemVO;

import io.vavr.control.Try;

@Component
public class ApprovalWorkflowTargetQueryService {

  @Autowired
  FlowTaskService flowTaskService;

  @Autowired
  FlowDefinitionService flowDefinitionService;

  @Autowired(required = false)
  FlowInstanceService flowInstanceService;

  @Autowired
  UsernameResolverRegistry usernameResolverRegistry;

  public List<TargetItemVO> getTransferTargets(String taskId, String userId) {
    requireTaskParticipant(taskId, userId);
    return getTransferTargetsInternal(taskId);
  }

  private List<TargetItemVO> getTransferTargetsInternal(String taskId) {
    return resolveUserTargets(taskId);
  }

  public List<TargetItemVO> getAddSignTargets(String taskId, String userId) {
    requireTaskParticipant(taskId, userId);
    return getAddSignTargetsInternal(taskId);
  }

  private List<TargetItemVO> getAddSignTargetsInternal(String taskId) {
    return resolveUserTargets(taskId);
  }

  public List<TargetItemVO> getFreeApprovalTargets(String taskId, String userId) {
    requireTaskParticipant(taskId, userId);
    return getFreeApprovalTargetsInternal(taskId);
  }

  private List<TargetItemVO> getFreeApprovalTargetsInternal(String taskId) {
    return resolveUserTargets(taskId);
  }

  public List<TargetItemVO> getFreeRejectTargets(String taskId, String userId) {
    requireTaskParticipant(taskId, userId);
    return getFreeRejectTargetsInternal(taskId);
  }

  private List<TargetItemVO> getFreeRejectTargetsInternal(String taskId) {
    return resolveFreeRejectTargets(taskId);
  }

  public List<TargetItemVO> getRejectTargets(String taskId, String userId) {
    requireTaskParticipant(taskId, userId);
    return getRejectTargetsInternal(taskId);
  }

  private List<TargetItemVO> getRejectTargetsInternal(String taskId) {
    return resolveRejectTargets(taskId);
  }

  public List<TargetItemVO> getRecoveryTargets(String taskId, String userId) {
    requireTaskParticipant(taskId, userId);
    return getRecoveryTargetsInternal(taskId);
  }

  private List<TargetItemVO> getRecoveryTargetsInternal(String taskId) {
    return resolveRecoveryTargets(taskId);
  }

  private List<TargetItemVO> resolveUserTargets(String taskId) {
    ensureTaskExists(taskId);
    List<String> candidateUserIds = Optional.ofNullable(flowTaskService.getCandidateUsers(taskId))
        .orElse(Collections.emptyList())
        .stream()
        .filter(StringUtils::isNotBlank)
        .distinct()
        .collect(Collectors.toList());
    String currentAssignee = flowTaskService.getTaskAssignee(taskId);
    if (StringUtils.isNotBlank(currentAssignee)) {
      candidateUserIds = candidateUserIds.stream()
          .filter(candidateUserId -> !StringUtils.equals(candidateUserId, currentAssignee))
          .collect(Collectors.toList());
    }

    Map<String, String> usernameMap = usernameResolverRegistry.resolve(candidateUserIds);
    return candidateUserIds.stream()
        .map(candidateUserId -> toTargetItem(candidateUserId, usernameMap.get(candidateUserId)))
        .collect(Collectors.toList());
  }

  private List<TargetItemVO> resolveFreeRejectTargets(String taskId) {
    FlowTaskDetail taskDetail = requireTaskDetail(taskId);
    Set<String> executedNodeIds = resolveExecutedNodeIds(taskDetail.getInstanceId());
    if (executedNodeIds.isEmpty()) {
      return Collections.emptyList();
    }
    return resolveDirectionalNodeTargets(taskDetail, false, resolveAncestorNodeIds(taskDetail))
        .stream()
        .filter(node -> executedNodeIds.contains(node.getId()))
        .map(node -> toNodeTargetItem(node.getId(), node.getName()))
        .collect(Collectors.toList());
  }

  private List<TargetItemVO> resolveRecoveryTargets(String taskId) {
    FlowTaskDetail taskDetail = requireTaskDetail(taskId);
    Set<String> terminalNodeIds = resolveTerminalNodeIds(taskDetail.getInstanceId());
    Set<String> activeNodeIds = resolveActiveNodeIds(taskDetail.getInstanceId());
    return resolveDirectionalNodeTargets(taskDetail, true, resolveDescendantNodeIds(taskDetail))
        .stream()
        .filter(node -> !terminalNodeIds.contains(node.getId()))
        .filter(node -> !activeNodeIds.contains(node.getId()))
        .map(node -> toNodeTargetItem(node.getId(), node.getName()))
        .collect(Collectors.toList());
  }

  private List<FlowNode> resolveDirectionalNodeTargets(FlowTaskDetail taskDetail, boolean downstreamOnly, Set<String> reachableNodeIds) {
    if (StringUtils.isBlank(taskDetail.getFlowKey()) || StringUtils.isBlank(taskDetail.getTaskDefinitionKey())) {
      return Collections.emptyList();
    }

    List<FlowNode> flowNodes = Optional.ofNullable(flowDefinitionService.getFlowNodes(taskDetail.getFlowKey()))
        .orElse(Collections.emptyList());
    if (flowNodes.isEmpty()) {
      return Collections.emptyList();
    }

    int currentIndex = resolveCurrentNodeIndex(flowNodes, taskDetail.getTaskDefinitionKey());
    if (currentIndex < 0) {
      return Collections.emptyList();
    }

    List<FlowNode> targetNodes = new ArrayList<FlowNode>();
    int startIndex = downstreamOnly ? currentIndex + 1 : 0;
    int endIndex = downstreamOnly ? flowNodes.size() : currentIndex;
    for (int index = startIndex; index < endIndex; index++) {
      FlowNode node = flowNodes.get(index);
      if (node instanceof ApprovalNode && (reachableNodeIds.isEmpty() || reachableNodeIds.contains(node.getId()))) {
        targetNodes.add(node);
      }
    }
    return targetNodes;
  }

  private int resolveCurrentNodeIndex(List<FlowNode> flowNodes, String taskDefinitionKey) {
    for (int index = 0; index < flowNodes.size(); index++) {
      if (Objects.equals(flowNodes.get(index).getId(), taskDefinitionKey)) {
        return index;
      }
    }
    return -1;
  }

  private List<TargetItemVO> resolveRejectTargets(String taskId) {
    FlowTaskDetail taskDetail = requireTaskDetail(taskId);
    if (StringUtils.isBlank(taskDetail.getFlowKey()) || StringUtils.isBlank(taskDetail.getTaskDefinitionKey())) {
      return Collections.emptyList();
    }

    List<FlowNode> flowNodes = Optional.ofNullable(flowDefinitionService.getFlowNodes(taskDetail.getFlowKey()))
        .orElse(Collections.emptyList());
    if (flowNodes.isEmpty()) {
      return Collections.emptyList();
    }

    return flowNodes.stream()
        .filter(node -> Objects.equals(node.getId(), taskDetail.getTaskDefinitionKey()))
        .filter(ApprovalNode.class::isInstance)
        .map(ApprovalNode.class::cast)
        .filter(ApprovalNode::isAllowRejectToSelf)
        .map(node -> toNodeTargetItem(node.getId(), node.getName()))
        .collect(Collectors.toList());
  }

  private TargetItemVO toTargetItem(String id, String label) {
    TargetItemVO targetItem = new TargetItemVO();
    targetItem.setId(id);
    targetItem.setLabel(StringUtils.defaultIfBlank(label, id));
    targetItem.setType("user");
    targetItem.setDisabled(null);
    targetItem.setDisabledReason(null);
    return targetItem;
  }

  private TargetItemVO toNodeTargetItem(String id, String label) {
    TargetItemVO targetItem = new TargetItemVO();
    targetItem.setId(id);
    targetItem.setLabel(StringUtils.defaultIfBlank(label, id));
    targetItem.setType("node");
    targetItem.setDisabled(null);
    targetItem.setDisabledReason(null);
    return targetItem;
  }

  private Set<String> resolveExecutedNodeIds(String instanceId) {
    return resolveTimeline(instanceId)
        .stream()
        .filter(this::hasCreatedTaskInstance)
        .map(FlowTimeline::getFlowNode)
        .filter(Objects::nonNull)
        .map(FlowNode::getId)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toCollection(HashSet::new));
  }

  private Set<String> resolveTerminalNodeIds(String instanceId) {
    return resolveTimeline(instanceId)
        .stream()
        .filter(this::isTerminalTimeline)
        .map(FlowTimeline::getFlowNode)
        .filter(Objects::nonNull)
        .map(FlowNode::getId)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toCollection(HashSet::new));
  }

  private Set<String> resolveActiveNodeIds(String instanceId) {
    if (flowInstanceService == null || StringUtils.isBlank(instanceId)) {
      return Collections.emptySet();
    }
    return Optional.ofNullable(flowInstanceService.getActiveActivityIds(instanceId))
        .orElse(Collections.emptyList())
        .stream()
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toCollection(HashSet::new));
  }

  private Set<String> resolveAncestorNodeIds(FlowTaskDetail taskDetail) {
    return resolveReachableNodeIds(taskDetail, false);
  }

  private Set<String> resolveDescendantNodeIds(FlowTaskDetail taskDetail) {
    return resolveReachableNodeIds(taskDetail, true);
  }

  private Set<String> resolveReachableNodeIds(FlowTaskDetail taskDetail, boolean downstreamOnly) {
    Optional<StructuredFlowDefinition> definition = resolveStructuredDefinition(taskDetail);
    if (!definition.isPresent()) {
      return Collections.emptySet();
    }
    StructuredFlowDefinition structuredDefinition = definition.get();
    if (structuredDefinition.getNode(taskDetail.getTaskDefinitionKey()) == null) {
      return Collections.emptySet();
    }

    Set<String> reachableNodeIds = new LinkedHashSet<String>();
    Deque<String> queue = new ArrayDeque<String>();
    queue.add(taskDetail.getTaskDefinitionKey());

    while (!queue.isEmpty()) {
      String currentNodeId = queue.removeFirst();
      List<FlowEdgeV2> edges = downstreamOnly
          ? structuredDefinition.getOutgoingEdges(currentNodeId)
          : structuredDefinition.getIncomingEdges(currentNodeId);
      for (FlowEdgeV2 edge : edges) {
        String nextNodeId = downstreamOnly ? edge.getTargetId() : edge.getSourceId();
        if (StringUtils.isBlank(nextNodeId) || !reachableNodeIds.add(nextNodeId)) {
          continue;
        }
        queue.addLast(nextNodeId);
      }
    }

    reachableNodeIds.remove(taskDetail.getTaskDefinitionKey());
    return reachableNodeIds;
  }

  private Optional<StructuredFlowDefinition> resolveStructuredDefinition(FlowTaskDetail taskDetail) {
    if (flowInstanceService == null || StringUtils.isBlank(taskDetail.getInstanceId())) {
      return Optional.empty();
    }
    Try<com.fasterxml.jackson.databind.JsonNode> rawDefinitionTry = flowInstanceService.getInstanceFlowDefinitionV2(taskDetail.getInstanceId());
    if (rawDefinitionTry == null || rawDefinitionTry.isFailure()) {
      return Optional.empty();
    }
    Try<StructuredFlowDefinition> definitionTry = rawDefinitionTry
        .map(jsonNode -> Json.toBean(StructuredFlowDefinition.class, jsonNode.toString()));
    if (definitionTry.isFailure()) {
      return Optional.empty();
    }
    return definitionTry.toJavaOptional();
  }

  private List<FlowTimeline> resolveTimeline(String instanceId) {
    if (flowInstanceService == null || StringUtils.isBlank(instanceId)) {
      return Collections.emptyList();
    }
    return Optional.ofNullable(flowInstanceService.getTimeLine(instanceId))
        .orElse(Collections.emptyList());
  }

  private boolean hasCreatedTaskInstance(FlowTimeline timeline) {
    return Optional.ofNullable(timeline.getTaskInstances())
        .orElse(Collections.emptyList())
        .stream()
        .anyMatch(taskInstance -> taskInstance.getState() != TaskState.UNCREATED);
  }

  private boolean isTerminalTimeline(FlowTimeline timeline) {
    return timeline.getState() == TaskState.APPROVED
        || timeline.getState() == TaskState.REJECTED
        || timeline.getState() == TaskState.HANDLED
        || timeline.getState() == TaskState.CANCELLED;
  }

  private void ensureTaskExists(String taskId) {
    requireTaskDetail(taskId);
  }

  /**
   * 动作目标查询的归属校验：只有该任务的当前处理人或候选人可以查询动作目标。
   *
   * <p>此前这六个查询只校验"任务存在"，任何持有审批中心权限的用户只要拿到 taskId 就能枚举
   * 他人任务的转办对象、加签对象、驳回/恢复目标节点与参与人名单。外部集成设计 §"动作与结果映射"
   * 第 2 条要求"使用当前用户、真实任务状态和平台权限做前置校验"。
   */
  private void requireTaskParticipant(String taskId, String userId) {
    if (StringUtils.isBlank(userId)) {
      throw new AccessDeniedException("无权访问该任务");
    }
    FlowTaskDetail taskDetail = requireTaskDetail(taskId);
    if (StringUtils.equals(userId, taskDetail.getAssignee())) {
      return;
    }
    boolean candidate = Optional.ofNullable(taskDetail.getCandidateUsers())
        .orElse(Collections.emptyList())
        .stream()
        .anyMatch(candidateUserId -> StringUtils.equals(userId, candidateUserId));
    if (!candidate) {
      throw new AccessDeniedException("无权访问该任务: " + taskId);
    }
  }

  private FlowTaskDetail requireTaskDetail(String taskId) {
    FlowTaskDetail taskDetail = flowTaskService.getTaskDetail(taskId);
    if (taskDetail == null) {
      throw new PlatformException("任务不存在");
    }
    return taskDetail;
  }
}
