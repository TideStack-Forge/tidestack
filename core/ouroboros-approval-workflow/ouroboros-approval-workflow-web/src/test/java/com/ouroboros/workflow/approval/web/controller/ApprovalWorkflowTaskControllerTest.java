package com.ouroboros.workflow.approval.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.control.Try;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.util.Json;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.web.service.ApprovalWorkflowTargetQueryService;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;
import com.ouroboros.workflow.approval.web.vo.AddSignTaskOptionsVO;
import com.ouroboros.workflow.approval.web.vo.AddSignPosition;
import com.ouroboros.workflow.approval.web.vo.FreeApprovalTaskOptionsVO;
import com.ouroboros.workflow.approval.web.vo.FreeRejectTaskOptionsVO;
import com.ouroboros.workflow.approval.web.vo.RecoveryTaskOptionsVO;
import com.ouroboros.workflow.approval.web.vo.SubmitTaskVO;
import com.ouroboros.workflow.approval.web.vo.TargetItemVO;
import com.ouroboros.workflow.approval.web.vo.TransferTaskOptionsVO;

class ApprovalWorkflowTaskControllerTest {

  @Test
  void descriptionOnlyTaskRendersGuidanceWithoutDeadActionButtons() {
    var controller = new ApprovalWorkflowTaskController();
    controller.flowTaskService = org.mockito.Mockito.mock(com.ouroboros.workflow.approval.FlowTaskService.class);
    org.mockito.Mockito.when(controller.flowTaskService.getTaskForm("task-1")).thenReturn(io.vavr.control.Try.failure(
        new com.ouroboros.workflow.approval.FlowFormServiceUnavailableException("custom")));
    var result = controller.getTaskFormSchema("task-1");
    org.junit.jupiter.api.Assertions.assertEquals(0, result.getCode());
    org.junit.jupiter.api.Assertions.assertEquals("alert", result.getData().getType());
    org.junit.jupiter.api.Assertions.assertNull(result.getData().getProp("actions"));
    org.junit.jupiter.api.Assertions.assertTrue(result.getData().getProp("body").toString().contains("业务页面"));
  }

  @Test
  void getTodoTasksShouldResolveStarterNamesThroughRegistry() {
    FlowTaskService flowTaskService = mock(FlowTaskService.class);
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    FlowTaskDetail taskDetail = new FlowTaskDetail();
    taskDetail.setAssignee("anonymousUser");
    taskDetail.setCandidateUsers(java.util.Collections.singletonList("anonymousUser"));
    taskDetail.setId("task-1");
    taskDetail.setStarter("employee-1");

    ApprovalWorkflowTaskController controller = new ApprovalWorkflowTaskController();
    controller.flowTaskService = flowTaskService;
    controller.workflowModelService = workflowModelService;
    controller.usernameResolverRegistry = usernameResolverRegistry;

    when(flowTaskService.getTaskCount("anonymousUser")).thenReturn(1L);
    when(flowTaskService.getTaskList("anonymousUser", 0, 10)).thenReturn(Collections.singletonList(taskDetail));
    when(workflowModelService.searchList(anyMap()))
        .thenReturn(Collections.emptyList());
    when(usernameResolverRegistry.resolve(Collections.singletonList("employee-1")))
        .thenReturn(Collections.singletonMap("employee-1", "张三"));

    PagedWebApiResult<?> result = controller.getTodoTasks(1, 10);
    Map<?, ?> item = (Map<?, ?>) result.getData().getItems().get(0);

    assertEquals("张三", item.get("starterName"));
  }

  @Test
  void submitTaskShouldMapTransferPayloadByAction() {
    FlowTaskService flowTaskService = mock(FlowTaskService.class);
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    ApprovalWorkflowTaskController controller = new ApprovalWorkflowTaskController();
    controller.flowTaskService = flowTaskService;
    controller.workflowModelService = workflowModelService;
    controller.usernameResolverRegistry = usernameResolverRegistry;

    SubmitTaskVO submitTaskVO = new SubmitTaskVO();
    submitTaskVO.setAction("transfer");
    submitTaskVO.setComment("transfer");
    Map<String, Object> payload = Collections.<String, Object>singletonMap("targetUserId", "user-b");
    submitTaskVO.setPayload(payload);

    when(flowTaskService.doAction(eq("task-1"), eq("transfer"), eq("anonymousUser"), eq("transfer"), eq(null), any()))
        .thenReturn(Try.success(null));

    WebApiResult<Void> result = controller.submitTask("task-1", submitTaskVO);

    assertEquals(0, result.getCode());
    verify(flowTaskService).doAction("task-1", "transfer", "anonymousUser", "transfer", null, payload);
  }

  @Test
  void submitTaskShouldMapTypedPayloadsForEnhancedActions() {
    FlowTaskService flowTaskService = mock(FlowTaskService.class);
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    ApprovalWorkflowTaskController controller = new ApprovalWorkflowTaskController();
    controller.flowTaskService = flowTaskService;
    controller.workflowModelService = workflowModelService;
    controller.usernameResolverRegistry = usernameResolverRegistry;

    Map<String, Object> freeReject = Collections.<String, Object>singletonMap("targetNodeId", "approve-a");
    Map<String, Object> recovery = Collections.<String, Object>singletonMap("targetNodeId", "approve-c");
    Map<String, Object> addSign = Collections.<String, Object>singletonMap("targetUserIds", Collections.singletonList("consult-a"));
    Map<String, Object> freeApproval = Collections.<String, Object>singletonMap("consultUserIds", Collections.singletonList("consult-b"));

    when(flowTaskService.doAction(eq("task-1"), eq("freeReject"), eq("anonymousUser"), eq(null), eq(null), any()))
        .thenReturn(Try.success(null));
    when(flowTaskService.doAction(eq("task-1"), eq("freeRecover"), eq("anonymousUser"), eq(null), eq(null), any()))
        .thenReturn(Try.success(null));
    when(flowTaskService.doAction(eq("task-1"), eq("addSign"), eq("anonymousUser"), eq(null), eq(null), any()))
        .thenReturn(Try.success(null));
    when(flowTaskService.doAction(eq("task-1"), eq("freeApprove"), eq("anonymousUser"), eq(null), eq(null), any()))
        .thenReturn(Try.success(null));

    SubmitTaskVO freeRejectRequest = new SubmitTaskVO();
    freeRejectRequest.setAction("freeReject");
    freeRejectRequest.setPayload(freeReject);
    controller.submitTask("task-1", freeRejectRequest);
    verify(flowTaskService).doAction("task-1", "freeReject", "anonymousUser", null, null, freeReject);

    SubmitTaskVO recoveryRequest = new SubmitTaskVO();
    recoveryRequest.setAction("freeRecover");
    RecoveryTaskOptionsVO recoveryOptions = new RecoveryTaskOptionsVO();
    recoveryOptions.setTargetNodeId("approve-c");
    recoveryRequest.setRecovery(recoveryOptions);
    controller.submitTask("task-1", recoveryRequest);
    verify(flowTaskService).doAction("task-1", "freeRecover", "anonymousUser", null, null, recovery);

    SubmitTaskVO addSignRequest = new SubmitTaskVO();
    addSignRequest.setAction("addSign");
    addSignRequest.setPayload(addSign);
    controller.submitTask("task-1", addSignRequest);
    verify(flowTaskService).doAction("task-1", "addSign", "anonymousUser", null, null, addSign);

    SubmitTaskVO freeApprovalRequest = new SubmitTaskVO();
    freeApprovalRequest.setAction("freeApprove");
    freeApprovalRequest.setPayload(freeApproval);
    controller.submitTask("task-1", freeApprovalRequest);
    verify(flowTaskService).doAction("task-1", "freeApprove", "anonymousUser", null, null, freeApproval);
  }

  @Test
  void queryTargetsEndpointsShouldReturnEmptyListsByDefault() {
    ApprovalWorkflowTargetQueryService targetQueryService = mock(ApprovalWorkflowTargetQueryService.class);
    TargetItemVO transferItem = targetItem("user-2", "张三", "user");
    TargetItemVO addSignItem = targetItem("user-2", "张三", "user");
    TargetItemVO freeApprovalItem = targetItem("user-2", "张三", "user");
    TargetItemVO freeRejectItem = targetItem("approve-a", "经理审批", "node");
    TargetItemVO rejectItem = targetItem("approve-a", "经理审批", "node");
    TargetItemVO recoveryItem = targetItem("approve-c", "财务审批", "node");

    ApprovalWorkflowTaskController controller = new ApprovalWorkflowTaskController();
    setField(controller, "approvalWorkflowTargetQueryService", targetQueryService);

    when(targetQueryService.getTransferTargets("task-1", "anonymousUser")).thenReturn(Collections.singletonList(transferItem));
    when(targetQueryService.getAddSignTargets("task-1", "anonymousUser")).thenReturn(Collections.singletonList(addSignItem));
    when(targetQueryService.getFreeApprovalTargets("task-1", "anonymousUser")).thenReturn(Collections.singletonList(freeApprovalItem));
    when(targetQueryService.getFreeRejectTargets("task-1", "anonymousUser")).thenReturn(Collections.singletonList(freeRejectItem));
    when(targetQueryService.getRejectTargets("task-1", "anonymousUser")).thenReturn(Collections.singletonList(rejectItem));
    when(targetQueryService.getRecoveryTargets("task-1", "anonymousUser")).thenReturn(Collections.singletonList(recoveryItem));

    WebApiResult<List<TargetItemVO>> transferTargets = controller.getTransferTargets("task-1");
    WebApiResult<List<TargetItemVO>> addSignTargets = controller.getAddSignTargets("task-1");
    WebApiResult<List<TargetItemVO>> freeApprovalTargets = controller.getFreeApprovalTargets("task-1");
    WebApiResult<List<TargetItemVO>> freeRejectTargets = controller.getFreeRejectTargets("task-1");
    WebApiResult<List<TargetItemVO>> rejectTargets = controller.getRejectTargets("task-1");
    WebApiResult<List<TargetItemVO>> recoveryTargets = controller.getRecoveryTargets("task-1");

    assertEquals(Collections.singletonList(transferItem), transferTargets.getData());
    assertEquals(Collections.singletonList(addSignItem), addSignTargets.getData());
    assertEquals(Collections.singletonList(freeApprovalItem), freeApprovalTargets.getData());
    assertEquals(Collections.singletonList(freeRejectItem), freeRejectTargets.getData());
    assertEquals(Collections.singletonList(rejectItem), rejectTargets.getData());
    assertEquals(Collections.singletonList(recoveryItem), recoveryTargets.getData());

    verify(targetQueryService).getTransferTargets("task-1", "anonymousUser");
    verify(targetQueryService).getAddSignTargets("task-1", "anonymousUser");
    verify(targetQueryService).getFreeApprovalTargets("task-1", "anonymousUser");
    verify(targetQueryService).getFreeRejectTargets("task-1", "anonymousUser");
    verify(targetQueryService).getRejectTargets("task-1", "anonymousUser");
    verify(targetQueryService).getRecoveryTargets("task-1", "anonymousUser");
  }

  @Test
  void queryTargetsEndpointsShouldPropagateTaskMissingException() {
    ApprovalWorkflowTargetQueryService targetQueryService = mock(ApprovalWorkflowTargetQueryService.class);
    ApprovalWorkflowTaskController controller = new ApprovalWorkflowTaskController();
    setField(controller, "approvalWorkflowTargetQueryService", targetQueryService);

    when(targetQueryService.getTransferTargets("task-missing", "anonymousUser"))
        .thenThrow(new PlatformException("任务不存在"));

    PlatformException exception = assertThrows(PlatformException.class,
        () -> controller.getTransferTargets("task-missing"));

    assertEquals("任务不存在", exception.getMessage());
  }

  @Test
  void targetQueryServiceShouldResolveUserAndNodeTargetsWithFallbacks() {
    FlowTaskService flowTaskService = mock(FlowTaskService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    ApprovalWorkflowTargetQueryService targetQueryService = new ApprovalWorkflowTargetQueryService();
    setField(targetQueryService, "flowTaskService", flowTaskService);
    setField(targetQueryService, "flowDefinitionService", flowDefinitionService);
    setField(targetQueryService, "flowInstanceService", flowInstanceService);
    setField(targetQueryService, "usernameResolverRegistry", usernameResolverRegistry);

    when(flowTaskService.getCandidateUsers("task-1")).thenReturn(Arrays.asList("employee-1", "employee-2", "employee-1"));
    when(flowTaskService.getTaskAssignee("task-1")).thenReturn("employee-1");
    when(usernameResolverRegistry.resolve(Collections.singletonList("employee-2")))
        .thenReturn(Collections.singletonMap("employee-2", "张三"));

    FlowTaskDetail taskDetail = new FlowTaskDetail();
    taskDetail.setAssignee("anonymousUser");
    taskDetail.setCandidateUsers(java.util.Collections.singletonList("anonymousUser"));
    taskDetail.setFlowKey("flow-a");
    taskDetail.setTaskDefinitionKey("approve-b");
    taskDetail.setInstanceId("instance-1");
    when(flowTaskService.getTaskDetail("task-1")).thenReturn(taskDetail);
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Arrays.asList(
        node("start-a", "发起节点"),
        approvalNode("approve-a", "经理审批"),
        approvalNode("approve-b", "", true),
        approvalNode("approve-c", "财务审批")
    ));
    when(flowInstanceService.getTimeLine("instance-1")).thenReturn(Arrays.asList(
        timeline("approve-a", "经理审批", TaskState.APPROVED, true),
        timeline("approve-b", "直属审批", TaskState.PROCESSING, true)
    ));
    when(flowInstanceService.getActiveActivityIds("instance-1")).thenReturn(Collections.<String>emptyList());

    List<TargetItemVO> transferTargets = targetQueryService.getTransferTargets("task-1", "anonymousUser");
    List<TargetItemVO> addSignTargets = targetQueryService.getAddSignTargets("task-1", "anonymousUser");
    List<TargetItemVO> freeApprovalTargets = targetQueryService.getFreeApprovalTargets("task-1", "anonymousUser");
    List<TargetItemVO> freeRejectTargets = targetQueryService.getFreeRejectTargets("task-1", "anonymousUser");
    List<TargetItemVO> rejectTargets = targetQueryService.getRejectTargets("task-1", "anonymousUser");
    List<TargetItemVO> recoveryTargets = targetQueryService.getRecoveryTargets("task-1", "anonymousUser");

    assertEquals(1, transferTargets.size());
    assertEquals("employee-2", transferTargets.get(0).getId());
    assertEquals("张三", transferTargets.get(0).getLabel());
    assertEquals("user", transferTargets.get(0).getType());
    assertEquals(1, addSignTargets.size());
    assertEquals("employee-2", addSignTargets.get(0).getId());
    assertEquals("张三", addSignTargets.get(0).getLabel());
    assertEquals("user", addSignTargets.get(0).getType());
    assertEquals(1, freeApprovalTargets.size());
    assertEquals("employee-2", freeApprovalTargets.get(0).getId());
    assertEquals("张三", freeApprovalTargets.get(0).getLabel());
    assertEquals("user", freeApprovalTargets.get(0).getType());

    assertEquals(Arrays.asList("approve-a"), freeRejectTargets.stream().map(TargetItemVO::getId).collect(Collectors.toList()));
    assertEquals(Arrays.asList("approve-b"), rejectTargets.stream().map(TargetItemVO::getId).collect(Collectors.toList()));
    assertEquals(Arrays.asList("approve-c"), recoveryTargets.stream().map(TargetItemVO::getId).collect(Collectors.toList()));
    assertEquals("经理审批", freeRejectTargets.get(0).getLabel());
    assertEquals("approve-b", rejectTargets.get(0).getLabel());
    assertEquals("财务审批", recoveryTargets.get(0).getLabel());
    assertEquals("node", freeRejectTargets.get(0).getType());
    assertEquals("node", rejectTargets.get(0).getType());
    assertEquals("node", recoveryTargets.get(0).getType());
  }

  @Test
  void targetQueryServiceShouldReturnEmptyRejectTargetsWhenSelfRejectDisabled() {
    FlowTaskService flowTaskService = mock(FlowTaskService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    ApprovalWorkflowTargetQueryService targetQueryService = new ApprovalWorkflowTargetQueryService();
    setField(targetQueryService, "flowTaskService", flowTaskService);
    setField(targetQueryService, "flowDefinitionService", flowDefinitionService);
    setField(targetQueryService, "flowInstanceService", flowInstanceService);
    setField(targetQueryService, "usernameResolverRegistry", usernameResolverRegistry);

    FlowTaskDetail taskDetail = new FlowTaskDetail();
    taskDetail.setAssignee("anonymousUser");
    taskDetail.setCandidateUsers(java.util.Collections.singletonList("anonymousUser"));
    taskDetail.setFlowKey("flow-a");
    taskDetail.setTaskDefinitionKey("approve-b");
    when(flowTaskService.getTaskDetail("task-1")).thenReturn(taskDetail);
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Arrays.asList(
        node("start-a", "发起节点"),
        approvalNode("approve-a", "经理审批"),
        approvalNode("approve-b", "直属审批", false),
        approvalNode("approve-c", "财务审批")
    ));

    assertTrue(targetQueryService.getRejectTargets("task-1", "anonymousUser").isEmpty());
  }

  @Test
  void targetQueryServiceShouldReturnEmptyListsWhenTaskOrNodesAreMissing() {
    FlowTaskService flowTaskService = mock(FlowTaskService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    ApprovalWorkflowTargetQueryService targetQueryService = new ApprovalWorkflowTargetQueryService();
    setField(targetQueryService, "flowTaskService", flowTaskService);
    setField(targetQueryService, "flowDefinitionService", flowDefinitionService);
    setField(targetQueryService, "flowInstanceService", flowInstanceService);
    setField(targetQueryService, "usernameResolverRegistry", usernameResolverRegistry);

    when(flowTaskService.getTaskDetail("task-1")).thenReturn(null);
    when(flowTaskService.getCandidateUsers("task-1")).thenReturn(Collections.<String>emptyList());

    PlatformException transferException = assertThrows(PlatformException.class,
        () -> targetQueryService.getTransferTargets("task-1", "anonymousUser"));
    PlatformException addSignException = assertThrows(PlatformException.class,
        () -> targetQueryService.getAddSignTargets("task-1", "anonymousUser"));
    PlatformException freeApprovalException = assertThrows(PlatformException.class,
        () -> targetQueryService.getFreeApprovalTargets("task-1", "anonymousUser"));
    PlatformException freeRejectException = assertThrows(PlatformException.class,
        () -> targetQueryService.getFreeRejectTargets("task-1", "anonymousUser"));
    PlatformException rejectException = assertThrows(PlatformException.class,
        () -> targetQueryService.getRejectTargets("task-1", "anonymousUser"));
    PlatformException recoveryException = assertThrows(PlatformException.class,
        () -> targetQueryService.getRecoveryTargets("task-1", "anonymousUser"));

    assertEquals("任务不存在", transferException.getMessage());
    assertEquals("任务不存在", addSignException.getMessage());
    assertEquals("任务不存在", freeApprovalException.getMessage());
    assertEquals("任务不存在", freeRejectException.getMessage());
    assertEquals("任务不存在", rejectException.getMessage());
    assertEquals("任务不存在", recoveryException.getMessage());
  }

  @Test
  void targetQueryServiceShouldKeepEmptyListsForMissingNodeContext() {
    FlowTaskService flowTaskService = mock(FlowTaskService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    ApprovalWorkflowTargetQueryService targetQueryService = new ApprovalWorkflowTargetQueryService();
    setField(targetQueryService, "flowTaskService", flowTaskService);
    setField(targetQueryService, "flowDefinitionService", flowDefinitionService);
    setField(targetQueryService, "flowInstanceService", flowInstanceService);
    setField(targetQueryService, "usernameResolverRegistry", usernameResolverRegistry);

    FlowTaskDetail taskDetail = new FlowTaskDetail();
    taskDetail.setAssignee("anonymousUser");
    taskDetail.setCandidateUsers(java.util.Collections.singletonList("anonymousUser"));
    taskDetail.setFlowKey("flow-a");
    taskDetail.setTaskDefinitionKey("approve-b");

    when(flowTaskService.getTaskDetail("task-1")).thenReturn(taskDetail);
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Collections.<FlowNode>emptyList());

    assertTrue(targetQueryService.getFreeRejectTargets("task-1", "anonymousUser").isEmpty());
    assertTrue(targetQueryService.getRejectTargets("task-1", "anonymousUser").isEmpty());
    assertTrue(targetQueryService.getRecoveryTargets("task-1", "anonymousUser").isEmpty());
  }

  @Test
  void targetQueryServiceShouldUseTimelineForFreeRejectAndRecoveryFiltering() {
    FlowTaskService flowTaskService = mock(FlowTaskService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    ApprovalWorkflowTargetQueryService targetQueryService = new ApprovalWorkflowTargetQueryService();
    setField(targetQueryService, "flowTaskService", flowTaskService);
    setField(targetQueryService, "flowDefinitionService", flowDefinitionService);
    setField(targetQueryService, "flowInstanceService", flowInstanceService);
    setField(targetQueryService, "usernameResolverRegistry", usernameResolverRegistry);

    FlowTaskDetail taskDetail = new FlowTaskDetail();
    taskDetail.setAssignee("anonymousUser");
    taskDetail.setCandidateUsers(java.util.Collections.singletonList("anonymousUser"));
    taskDetail.setFlowKey("flow-a");
    taskDetail.setTaskDefinitionKey("approve-c");
    taskDetail.setInstanceId("instance-2");
    when(flowTaskService.getTaskDetail("task-2")).thenReturn(taskDetail);
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Arrays.asList(
        node("start-a", "发起节点"),
        approvalNode("approve-a", "经理审批"),
        approvalNode("approve-b", "会签审批"),
        approvalNode("approve-c", "直属审批", true),
        approvalNode("approve-d", "财务审批"),
        approvalNode("approve-e", "法务审批")
    ));
    when(flowInstanceService.getTimeLine("instance-2")).thenReturn(Arrays.asList(
        timeline("approve-a", "经理审批", TaskState.APPROVED, true),
        timeline("approve-c", "直属审批", TaskState.PROCESSING, true),
        timeline("approve-d", "财务审批", TaskState.REJECTED, true)
    ));
    when(flowInstanceService.getActiveActivityIds("instance-2")).thenReturn(Collections.singletonList("approve-e"));

    assertEquals(Arrays.asList("approve-a"),
        targetQueryService.getFreeRejectTargets("task-2", "anonymousUser").stream().map(TargetItemVO::getId).collect(Collectors.toList()));
    assertTrue(targetQueryService.getRecoveryTargets("task-2", "anonymousUser").isEmpty());
  }

  @Test
  void targetQueryServiceShouldExcludeSiblingBranchNodesFromNodeTargets() {
    FlowTaskService flowTaskService = mock(FlowTaskService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    ApprovalWorkflowTargetQueryService targetQueryService = new ApprovalWorkflowTargetQueryService();
    setField(targetQueryService, "flowTaskService", flowTaskService);
    setField(targetQueryService, "flowDefinitionService", flowDefinitionService);
    setField(targetQueryService, "flowInstanceService", flowInstanceService);
    setField(targetQueryService, "usernameResolverRegistry", usernameResolverRegistry);

    FlowTaskDetail taskDetail = new FlowTaskDetail();
    taskDetail.setAssignee("anonymousUser");
    taskDetail.setCandidateUsers(java.util.Collections.singletonList("anonymousUser"));
    taskDetail.setFlowKey("flow-a");
    taskDetail.setTaskDefinitionKey("approve-a");
    taskDetail.setInstanceId("instance-branch");
    when(flowTaskService.getTaskDetail("task-branch")).thenReturn(taskDetail);
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Arrays.asList(
        node("start-a", "发起节点"),
        approvalNode("approve-a", "经理审批", true),
        approvalNode("approve-b", "直属审批"),
        approvalNode("approve-c", "财务审批")
    ));
    when(flowInstanceService.getTimeLine("instance-branch")).thenReturn(Arrays.asList(
        timeline("approve-a", "经理审批", TaskState.PROCESSING, true),
        timeline("approve-b", "直属审批", TaskState.APPROVED, true)
    ));
    when(flowInstanceService.getActiveActivityIds("instance-branch")).thenReturn(Collections.<String>emptyList());
    when(flowInstanceService.getInstanceFlowDefinitionV2("instance-branch"))
        .thenReturn(Try.success(Json.toBean(JsonNode.class, forkBranchFlowJson())));

    assertTrue(targetQueryService.getFreeRejectTargets("task-branch", "anonymousUser").isEmpty());
    assertEquals(Arrays.asList("approve-c"),
        targetQueryService.getRecoveryTargets("task-branch", "anonymousUser")
            .stream()
            .map(TargetItemVO::getId)
            .collect(Collectors.toList()));
  }

  @Test
  void nodeTargetsShouldResolveApprovalsFromDefinitionOrder() {
    FlowTaskService flowTaskService = mock(FlowTaskService.class);
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);

    ApprovalWorkflowTargetQueryService targetQueryService = new ApprovalWorkflowTargetQueryService();
    setField(targetQueryService, "flowTaskService", flowTaskService);
    setField(targetQueryService, "flowDefinitionService", flowDefinitionService);
    setField(targetQueryService, "flowInstanceService", flowInstanceService);
    setField(targetQueryService, "usernameResolverRegistry", usernameResolverRegistry);

    ApprovalWorkflowTaskController controller = new ApprovalWorkflowTaskController();
    controller.flowTaskService = flowTaskService;
    controller.workflowModelService = workflowModelService;
    controller.usernameResolverRegistry = usernameResolverRegistry;
    setField(controller, "approvalWorkflowTargetQueryService", targetQueryService);

    FlowTaskDetail taskDetail = new FlowTaskDetail();
    taskDetail.setAssignee("anonymousUser");
    taskDetail.setCandidateUsers(java.util.Collections.singletonList("anonymousUser"));
    taskDetail.setFlowKey("flow-a");
    taskDetail.setTaskDefinitionKey("approve-b");
    taskDetail.setInstanceId("instance-3");
    when(flowTaskService.getTaskDetail("task-1")).thenReturn(taskDetail);
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Arrays.asList(
        node("start-a", "发起节点"),
        approvalNode("approve-a", "经理审批"),
        approvalNode("approve-b", "直属审批", true),
        approvalNode("approve-c", "财务审批")
    ));
    when(flowInstanceService.getTimeLine("instance-3")).thenReturn(Arrays.asList(
        timeline("approve-a", "经理审批", TaskState.APPROVED, true),
        timeline("approve-b", "直属审批", TaskState.PROCESSING, true)
    ));
    when(flowInstanceService.getActiveActivityIds("instance-3")).thenReturn(Collections.<String>emptyList());

    WebApiResult<List<TargetItemVO>> freeRejectTargets = controller.getFreeRejectTargets("task-1");
    WebApiResult<List<TargetItemVO>> rejectTargets = controller.getRejectTargets("task-1");
    WebApiResult<List<TargetItemVO>> recoveryTargets = controller.getRecoveryTargets("task-1");

    assertEquals(Arrays.asList("approve-a"), freeRejectTargets.getData().stream().map(TargetItemVO::getId).collect(Collectors.toList()));
    assertEquals(Arrays.asList("approve-b"), rejectTargets.getData().stream().map(TargetItemVO::getId).collect(Collectors.toList()));
    assertEquals(Arrays.asList("approve-c"), recoveryTargets.getData().stream().map(TargetItemVO::getId).collect(Collectors.toList()));
    assertEquals("node", freeRejectTargets.getData().get(0).getType());
    assertEquals("node", rejectTargets.getData().get(0).getType());
    assertEquals("node", recoveryTargets.getData().get(0).getType());
  }

  @Test
  void getTaskFormSchemaShouldResolveTransferButtonReference() {
    ApprovalWorkflowTaskController controller = createController();
    SchemaNode taskForm = taskFormWithActions(Collections.singletonList("transfer"));

    when(controller.flowTaskService.getTaskForm("task-transfer")).thenReturn(Try.success(taskForm));

    WebApiResult<SchemaNode> result = controller.getTaskFormSchema("task-transfer");
    List<Map<String, String>> resolved = extractActionRefs(result.getData());

    assertTrue(resolved.stream().anyMatch(action -> "transferButton".equals(action.get("$ref"))),
        "Expected transfer action to resolve as transferButton reference");
  }

  @Test
  void getTaskFormSchemaShouldResolveAddSignButtonReference() {
    ApprovalWorkflowTaskController controller = createController();
    SchemaNode taskForm = taskFormWithActions(Collections.singletonList("addSign"));

    when(controller.flowTaskService.getTaskForm("task-add-sign")).thenReturn(Try.success(taskForm));

    WebApiResult<SchemaNode> result = controller.getTaskFormSchema("task-add-sign");
    List<Map<String, String>> resolved = extractActionRefs(result.getData());

    assertTrue(resolved.stream().anyMatch(action -> "addSignButton".equals(action.get("$ref"))),
        "Expected addSign action to resolve as addSignButton reference");
  }

  @Test
  void getTaskFormSchemaShouldResolveFreeRejectButtonReference() {
    ApprovalWorkflowTaskController controller = createController();
    SchemaNode taskForm = taskFormWithActions(Collections.singletonList("freeReject"));

    when(controller.flowTaskService.getTaskForm("task-free-reject")).thenReturn(Try.success(taskForm));

    WebApiResult<SchemaNode> result = controller.getTaskFormSchema("task-free-reject");
    List<Map<String, String>> resolved = extractActionRefs(result.getData());

    assertTrue(resolved.stream().anyMatch(action -> "freeRejectButton".equals(action.get("$ref"))),
        "Expected freeReject action to resolve as freeRejectButton reference");
  }

  @Test
  void getTaskFormSchemaShouldResolveFreeApprovalButtonReferenceFromActionAlias() {
    ApprovalWorkflowTaskController controller = createController();
    SchemaNode taskForm = taskFormWithActions(Collections.singletonList("freeApproval"));

    when(controller.flowTaskService.getTaskForm("task-free-approval")).thenReturn(Try.success(taskForm));

    WebApiResult<SchemaNode> result = controller.getTaskFormSchema("task-free-approval");
    List<Map<String, String>> resolved = extractActionRefs(result.getData());

    assertTrue(resolved.stream().anyMatch(action -> "freeApproveButton".equals(action.get("$ref"))),
        "Expected freeApproval action alias to resolve as freeApproveButton reference");
  }

  @Test
  void submitTaskShouldValidateTransferActionPayloadFromTypedOptions() {
    ApprovalWorkflowTaskController controller = createController();
    when(controller.flowTaskService.doAction(eq("task-1"), eq("transfer"), eq("anonymousUser"), eq(null), eq(null), any()))
        .thenReturn(Try.success(null));

    SubmitTaskVO submitTaskVO = new SubmitTaskVO();
    submitTaskVO.setAction("transfer");
    TransferTaskOptionsVO transfer = new TransferTaskOptionsVO();
    transfer.setTargetUserId("employee-2");
    submitTaskVO.setTransfer(transfer);

    controller.submitTask("task-1", submitTaskVO);

    verify(controller.flowTaskService).doAction("task-1", "transfer", "anonymousUser", null, null,
        Collections.<String, Object>singletonMap("targetUserId", "employee-2"));
  }

  @Test
  void submitTaskShouldValidateAddSignActionPayloadFromTypedOptions() {
    ApprovalWorkflowTaskController controller = createController();
    when(controller.flowTaskService.doAction(eq("task-1"), eq("addSign"), eq("anonymousUser"), eq(null), eq(null), any()))
        .thenReturn(Try.success(null));

    SubmitTaskVO submitTaskVO = new SubmitTaskVO();
    submitTaskVO.setAction("addSign");
    AddSignTaskOptionsVO addSign = new AddSignTaskOptionsVO();
    addSign.setTargetUserIds(Collections.singletonList("employee-3"));
    addSign.setPosition(AddSignPosition.BEFORE_CURRENT);
    submitTaskVO.setAddSign(addSign);

    controller.submitTask("task-1", submitTaskVO);

    verify(controller.flowTaskService).doAction("task-1", "addSign", "anonymousUser", null, null,
        mapOf("targetUserIds", Collections.singletonList("employee-3"), "position", "BEFORE_CURRENT"));
  }

  @Test
  void submitTaskShouldValidateFreeRejectActionPayloadFromTypedOptions() {
    ApprovalWorkflowTaskController controller = createController();
    when(controller.flowTaskService.doAction(eq("task-1"), eq("freeReject"), eq("anonymousUser"), eq(null), eq(null), any()))
        .thenReturn(Try.success(null));

    SubmitTaskVO submitTaskVO = new SubmitTaskVO();
    submitTaskVO.setAction("freeReject");
    FreeRejectTaskOptionsVO freeReject = new FreeRejectTaskOptionsVO();
    freeReject.setTargetNodeId("approve-b");
    submitTaskVO.setFreeReject(freeReject);

    controller.submitTask("task-1", submitTaskVO);

    verify(controller.flowTaskService).doAction("task-1", "freeReject", "anonymousUser", null, null,
        Collections.<String, Object>singletonMap("targetNodeId", "approve-b"));
  }

  @Test
  void submitTaskShouldValidateFreeRecoverActionPayloadFromTypedOptions() {
    ApprovalWorkflowTaskController controller = createController();
    when(controller.flowTaskService.doAction(eq("task-1"), eq("freeRecover"), eq("anonymousUser"), eq(null), eq(null), any()))
        .thenReturn(Try.success(null));

    SubmitTaskVO submitTaskVO = new SubmitTaskVO();
    submitTaskVO.setAction("freeRecover");
    RecoveryTaskOptionsVO recovery = new RecoveryTaskOptionsVO();
    recovery.setTargetNodeId("approve-c");
    submitTaskVO.setRecovery(recovery);

    controller.submitTask("task-1", submitTaskVO);

    verify(controller.flowTaskService).doAction("task-1", "freeRecover", "anonymousUser", null, null,
        Collections.<String, Object>singletonMap("targetNodeId", "approve-c"));
  }

  @Test
  void submitTaskShouldValidateFreeApprovalActionPayloadFromTypedOptions() {
    ApprovalWorkflowTaskController controller = createController();
    when(controller.flowTaskService.doAction(eq("task-1"), eq("freeApprove"), eq("anonymousUser"), eq(null), eq(null), any()))
        .thenReturn(Try.success(null));

    SubmitTaskVO submitTaskVO = new SubmitTaskVO();
    submitTaskVO.setAction("freeApprove");
    FreeApprovalTaskOptionsVO freeApproval = new FreeApprovalTaskOptionsVO();
    freeApproval.setConsultUserIds(Collections.singletonList("employee-4"));
    submitTaskVO.setFreeApproval(freeApproval);

    controller.submitTask("task-1", submitTaskVO);

    verify(controller.flowTaskService).doAction("task-1", "freeApprove", "anonymousUser", null, null,
        Collections.<String, Object>singletonMap("consultUserIds", Collections.singletonList("employee-4")));
  }

  @Test
  void submitTaskShouldRejectAdvancedActionWhenPayloadAndTypedOptionsAreBothMissing() {
    ApprovalWorkflowTaskController controller = createController();
    when(controller.flowTaskService.doAction(eq("task-1"), eq("transfer"), eq("anonymousUser"), eq(null), eq(null), any()))
        .thenReturn(Try.success(null));

    SubmitTaskVO submitTaskVO = new SubmitTaskVO();
    submitTaskVO.setAction("transfer");

    WebApiResult<Void> result = controller.submitTask("task-1", submitTaskVO);

    assertTrue(result.getCode() != 0, "Expected transfer submission to be rejected when options are missing");
    verify(controller.flowTaskService, never()).doAction(eq("task-1"), eq("transfer"), eq("anonymousUser"), eq(null), eq(null), any());
  }

  @Test
  void shouldExposeSelectorEndpointsForAdvancedActions() {
    List<String> endpointPatterns = listGetMappingPatterns(ApprovalWorkflowTaskController.class);

    assertTrue(endpointPatterns.contains("/{taskId}/transfer-targets"),
        "Missing transfer selector endpoint /{taskId}/transfer-targets");
    assertTrue(endpointPatterns.contains("/{taskId}/add-sign-targets"),
        "Missing add-sign selector endpoint /{taskId}/add-sign-targets");
    assertTrue(endpointPatterns.contains("/{taskId}/free-reject-targets"),
        "Missing free-reject selector endpoint /{taskId}/free-reject-targets");
    assertTrue(endpointPatterns.contains("/{taskId}/free-approval-targets"),
        "Missing free-approval selector endpoint /{taskId}/free-approval-targets");
    assertTrue(endpointPatterns.contains("/{taskId}/recovery-targets"),
        "Missing recovery selector endpoint /{taskId}/recovery-targets");
  }

  private ApprovalWorkflowTaskController createController() {
    FlowTaskService flowTaskService = mock(FlowTaskService.class);
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    ApprovalWorkflowTaskController controller = new ApprovalWorkflowTaskController();
    controller.flowTaskService = flowTaskService;
    controller.workflowModelService = workflowModelService;
    controller.usernameResolverRegistry = usernameResolverRegistry;
    return controller;
  }

  private SchemaNode taskFormWithActions(List<String> actions) {
    SchemaNode taskForm = new SchemaNode();
    taskForm.setType("form");
    taskForm.setProp("body", new ArrayList<SchemaNode>());
    taskForm.setProp("actions", actions);
    return taskForm;
  }

  private List<Map<String, String>> extractActionRefs(SchemaNode schemaNode) {
    Object actions = schemaNode.getProp("actions");
    assertNotNull(actions, "Task form actions should not be null");
    assertTrue(actions instanceof List, "Task form actions should be a list");
    List<?> actionEntries = (List<?>) actions;
    List<Map<String, String>> refs = new ArrayList<Map<String, String>>();
    for (Object actionEntry : actionEntries) {
      assertTrue(actionEntry instanceof Map,
          "Task form action entries must be reference maps, but got: " + actionEntry);
      Map<?, ?> actionMap = (Map<?, ?>) actionEntry;
      Object ref = actionMap.get("$ref");
      assertNotNull(ref, "Task form action reference map must include $ref");
      refs.add(Collections.singletonMap("$ref", ref.toString()));
    }
    return refs;
  }

  private List<String> listGetMappingPatterns(Class<?> controllerClass) {
    return java.util.Arrays.stream(controllerClass.getDeclaredMethods())
        .map(this::extractGetMappings)
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  private List<String> extractGetMappings(Method method) {
    GetMapping mapping = method.getAnnotation(GetMapping.class);
    if (mapping == null) {
      return Collections.emptyList();
    }
    if (mapping.value().length > 0) {
      return java.util.Arrays.asList(mapping.value());
    }
    return java.util.Arrays.asList(mapping.path());
  }

  private Map<String, Object> mapOf(String key1, Object value1, String key2, Object value2) {
    return java.util.stream.Stream.of(
            new java.util.AbstractMap.SimpleEntry<String, Object>(key1, value1),
            new java.util.AbstractMap.SimpleEntry<String, Object>(key2, value2))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private TargetItemVO targetItem(String id, String label, String type) {
    TargetItemVO targetItem = new TargetItemVO();
    targetItem.setId(id);
    targetItem.setLabel(label);
    targetItem.setType(type);
    return targetItem;
  }

  private String forkBranchFlowJson() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"start-a\", \"permission\": [] },",
        "    {",
        "      \"type\": \"审批节点\",",
        "      \"name\": \"经理审批\",",
        "      \"id\": \"approve-a\",",
        "      \"subType\": \"单签\",",
        "      \"candidates\": [{ \"type\": \"UserSelect\", \"mode\": \"single\" }],",
        "      \"permission\": [],",
        "      \"actions\": [\"reject\"]",
        "    },",
        "    {",
        "      \"type\": \"审批节点\",",
        "      \"name\": \"直属审批\",",
        "      \"id\": \"approve-b\",",
        "      \"subType\": \"单签\",",
        "      \"candidates\": [{ \"type\": \"UserSelect\", \"mode\": \"single\" }],",
        "      \"permission\": [],",
        "      \"actions\": [\"reject\"]",
        "    },",
        "    {",
        "      \"type\": \"审批节点\",",
        "      \"name\": \"财务审批\",",
        "      \"id\": \"approve-c\",",
        "      \"subType\": \"单签\",",
        "      \"candidates\": [{ \"type\": \"UserSelect\", \"mode\": \"single\" }],",
        "      \"permission\": [],",
        "      \"actions\": [\"reject\"]",
        "    }",
        "  ],",
        "  \"edges\": [",
        "    {",
        "      \"id\": \"e1\",",
        "      \"type\": \"SEQUENCE\",",
        "      \"sourceId\": \"start-a\",",
        "      \"targetId\": \"approve-a\",",
        "      \"name\": \"金额大于1000\",",
        "      \"condition\": {",
        "        \"left\": { \"type\": \"field\", \"field\": \"amount\" },",
        "        \"op\": \"greater\",",
        "        \"right\": 1000",
        "      },",
        "      \"order\": 1",
        "    },",
        "    {",
        "      \"id\": \"e2\",",
        "      \"type\": \"SEQUENCE\",",
        "      \"sourceId\": \"start-a\",",
        "      \"targetId\": \"approve-b\",",
        "      \"name\": \"默认\",",
        "      \"condition\": {",
        "        \"left\": { \"type\": \"field\", \"field\": \"amount\" },",
        "        \"op\": \"less_or_equal\",",
        "        \"right\": 1000",
        "      },",
        "      \"order\": 2",
        "    },",
        "    { \"id\": \"e3\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"approve-c\" },",
        "    { \"id\": \"e4\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"approve-c\" }",
        "  ]",
        "}");
  }

  private void setField(Object target, String fieldName, Object value) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }

  private FlowNode node(String id, String name) {
    FlowNode node = new FlowNode() {
      @Override
      public String getType() {
        return "节点";
      }
    };
    node.setId(id);
    node.setName(name);
    return node;
  }

  private FlowTimeline timeline(String nodeId, String nodeName, TaskState state, boolean withTaskInstance) {
    FlowTimeline timeline = new FlowTimeline();
    timeline.setFlowNode(approvalNode(nodeId, nodeName));
    timeline.setState(state);
    if (!withTaskInstance) {
      timeline.setTaskInstances(Collections.<FlowTimeline.TaskInstance>emptyList());
      return timeline;
    }

    FlowTimeline.TaskInstance taskInstance = new FlowTimeline.TaskInstance();
    taskInstance.setTaskId(nodeId + "-task");
    taskInstance.setInstanceId("instance");
    taskInstance.setState(state);
    timeline.setTaskInstances(Collections.singletonList(taskInstance));
    return timeline;
  }

  private ApprovalNode approvalNode(String id, String name) {
    return approvalNode(id, name, true);
  }

  private ApprovalNode approvalNode(String id, String name, boolean allowRejectToSelf) {
    ApprovalNode node = new SingleApprovalNode();
    node.setId(id);
    node.setName(name);
    node.setAllowRejectToSelf(allowRejectToSelf);
    return node;
  }
}
