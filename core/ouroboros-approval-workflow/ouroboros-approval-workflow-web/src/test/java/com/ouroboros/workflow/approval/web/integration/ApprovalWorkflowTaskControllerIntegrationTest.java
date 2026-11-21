package com.ouroboros.workflow.approval.web.integration;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.vavr.control.Try;

import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.definition.FlowNode;
import com.ouroboros.workflow.approval.definition.nodes.ApprovalNode;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.web.controller.ApprovalWorkflowTaskController;
import com.ouroboros.workflow.approval.web.service.ApprovalWorkflowTargetQueryService;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;

class ApprovalWorkflowTaskControllerIntegrationTest {
  private MockMvc mockMvc;
  private FlowTaskService flowTaskService;
  private WorkflowModelService workflowModelService;
  private UsernameResolverRegistry usernameResolverRegistry;
  private FlowDefinitionService flowDefinitionService;
  private FlowInstanceService flowInstanceService;
  private ApprovalWorkflowTargetQueryService approvalWorkflowTargetQueryService;

  @BeforeEach
  void setUp() {
    flowTaskService = mock(FlowTaskService.class);
    workflowModelService = mock(WorkflowModelService.class);
    usernameResolverRegistry = mock(UsernameResolverRegistry.class);
    flowDefinitionService = mock(FlowDefinitionService.class);
    flowInstanceService = mock(FlowInstanceService.class);
    approvalWorkflowTargetQueryService = new ApprovalWorkflowTargetQueryService();

    ApprovalWorkflowTaskController controller = new ApprovalWorkflowTaskController();
    setField(controller, "flowTaskService", flowTaskService);
    setField(controller, "workflowModelService", workflowModelService);
    setField(controller, "usernameResolverRegistry", usernameResolverRegistry);
    setField(controller, "approvalWorkflowTargetQueryService", approvalWorkflowTargetQueryService);
    setField(approvalWorkflowTargetQueryService, "flowTaskService", flowTaskService);
    setField(approvalWorkflowTargetQueryService, "flowDefinitionService", flowDefinitionService);
    setField(approvalWorkflowTargetQueryService, "flowInstanceService", flowInstanceService);
    setField(approvalWorkflowTargetQueryService, "usernameResolverRegistry", usernameResolverRegistry);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void submitAdvancedActionsMapsTypedPayloadsAndDispatchesCanonicalActionNames() throws Exception {
    when(flowTaskService.doAction(eq("task-transfer"), eq("transfer"), eq("anonymousUser"), eq("transfer"), anyMap(), anyMap()))
        .thenReturn(Try.success(null));
    when(flowTaskService.doAction(eq("task-add-sign"), eq("addSign"), eq("anonymousUser"), eq("add-sign"), anyMap(), anyMap()))
        .thenReturn(Try.success(null));
    when(flowTaskService.doAction(eq("task-free-reject"), eq("freeReject"), eq("anonymousUser"), eq("free-reject"), anyMap(), anyMap()))
        .thenReturn(Try.success(null));
    when(flowTaskService.doAction(eq("task-free-recover"), eq("freeRecover"), eq("anonymousUser"), eq("free-recover"), anyMap(), anyMap()))
        .thenReturn(Try.success(null));
    when(flowTaskService.doAction(eq("task-free-approve"), eq("freeApprove"), eq("anonymousUser"), eq("free-approve"), anyMap(), anyMap()))
        .thenReturn(Try.success(null));

    String transferBody = "{\"action\":\"transfer\",\"comment\":\"transfer\",\"formData\":{\"amount\":100},\"transfer\":{\"targetUserId\":\"user-b\"}}";
    String addSignBody = "{\"action\":\"addSign\",\"comment\":\"add-sign\",\"formData\":{\"amount\":100},\"addSign\":{\"position\":\"BEFORE_CURRENT\",\"targetUserIds\":[\"user-c\",\"user-d\"]}}";
    String freeRejectBody = "{\"action\":\"freeReject\",\"comment\":\"free-reject\",\"formData\":{\"amount\":100},\"freeReject\":{\"targetNodeId\":\"approve-a\"}}";
    String freeRecoverBody = "{\"action\":\"freeRecover\",\"comment\":\"free-recover\",\"formData\":{\"amount\":100},\"recovery\":{\"targetNodeId\":\"approve-c\"}}";
    String freeApproveBody = "{\"action\":\"freeApprove\",\"comment\":\"free-approve\",\"formData\":{\"amount\":100},\"freeApproval\":{\"consultUserIds\":[\"user-e\"]}}";

    mockMvc.perform(post("/api/approval-workflow/task/task-transfer").contentType(MediaType.APPLICATION_JSON).content(transferBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));
    mockMvc.perform(post("/api/approval-workflow/task/task-add-sign").contentType(MediaType.APPLICATION_JSON).content(addSignBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));
    mockMvc.perform(post("/api/approval-workflow/task/task-free-reject").contentType(MediaType.APPLICATION_JSON).content(freeRejectBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));
    mockMvc.perform(post("/api/approval-workflow/task/task-free-recover").contentType(MediaType.APPLICATION_JSON).content(freeRecoverBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));
    mockMvc.perform(post("/api/approval-workflow/task/task-free-approve").contentType(MediaType.APPLICATION_JSON).content(freeApproveBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    verify(flowTaskService).doAction("task-transfer", "transfer", "anonymousUser", "transfer",
        Collections.<String, Object>singletonMap("amount", 100),
        Collections.<String, Object>singletonMap("targetUserId", "user-b"));
    verify(flowTaskService).doAction("task-add-sign", "addSign", "anonymousUser", "add-sign",
        Collections.<String, Object>singletonMap("amount", 100),
        mapOf("targetUserIds", Arrays.asList("user-c", "user-d"), "position", "BEFORE_CURRENT"));
    verify(flowTaskService).doAction("task-free-reject", "freeReject", "anonymousUser", "free-reject",
        Collections.<String, Object>singletonMap("amount", 100),
        Collections.<String, Object>singletonMap("targetNodeId", "approve-a"));
    verify(flowTaskService).doAction("task-free-recover", "freeRecover", "anonymousUser", "free-recover",
        Collections.<String, Object>singletonMap("amount", 100),
        Collections.<String, Object>singletonMap("targetNodeId", "approve-c"));
    verify(flowTaskService).doAction("task-free-approve", "freeApprove", "anonymousUser", "free-approve",
        Collections.<String, Object>singletonMap("amount", 100),
        Collections.<String, Object>singletonMap("consultUserIds", Collections.singletonList("user-e")));
  }

  @Test
  void transferAndAddSignTargetsExposeResolvedUserLabels() throws Exception {
    when(flowTaskService.getTaskDetail("task-transfer")).thenReturn(taskDetail("flow-a", "approve-b", "instance-transfer"));
    when(flowTaskService.getCandidateUsers("task-transfer")).thenReturn(Arrays.asList("user-a", "user-b", "user-a"));
    when(flowTaskService.getTaskAssignee("task-transfer")).thenReturn("user-a");
    when(flowTaskService.getTaskDetail("task-add-sign")).thenReturn(taskDetail("flow-a", "approve-b", "instance-add-sign"));
    when(flowTaskService.getCandidateUsers("task-add-sign")).thenReturn(Arrays.asList("user-c", "user-d"));
    when(flowTaskService.getTaskAssignee("task-add-sign")).thenReturn("user-c");
    when(flowTaskService.getTaskDetail("task-free-approval")).thenReturn(taskDetail("flow-a", "approve-b", "instance-free-approval"));
    when(flowTaskService.getCandidateUsers("task-free-approval")).thenReturn(Arrays.asList("user-e", "user-f"));
    when(flowTaskService.getTaskAssignee("task-free-approval")).thenReturn("user-e");
    when(flowTaskService.getTaskDetail("task-free-reject")).thenReturn(taskDetail("flow-a", "approve-b", "instance-node-targets"));
    when(flowTaskService.getTaskDetail("task-reject")).thenReturn(taskDetail("flow-a", "approve-b", "instance-node-targets"));
    when(flowTaskService.getTaskDetail("task-recovery")).thenReturn(taskDetail("flow-a", "approve-b", "instance-node-targets"));
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Arrays.asList(
        node("start-a", "发起节点"),
        approvalNode("approve-a", "经理审批"),
        approvalNode("approve-b", "直属审批", true),
        approvalNode("approve-c", "财务审批")
    ));
    when(flowInstanceService.getTimeLine("instance-node-targets")).thenReturn(Arrays.asList(
        timeline("approve-a", "经理审批", TaskState.APPROVED, true),
        timeline("approve-b", "直属审批", TaskState.PROCESSING, true)
    ));
    when(flowInstanceService.getActiveActivityIds("instance-node-targets")).thenReturn(Collections.<String>emptyList());

    when(usernameResolverRegistry.resolve(Collections.singletonList("user-b")))
        .thenReturn(Collections.singletonMap("user-b", "张三"));
    when(usernameResolverRegistry.resolve(Collections.singletonList("user-d")))
        .thenReturn(Collections.singletonMap("user-d", "李四"));
    when(usernameResolverRegistry.resolve(Collections.singletonList("user-f")))
        .thenReturn(Collections.singletonMap("user-f", "王五"));

    mockMvc.perform(get("/api/approval-workflow/task/task-transfer/transfer-targets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value("user-b"))
        .andExpect(jsonPath("$.data[0].label").value("张三"))
        .andExpect(jsonPath("$.data[0].type").value("user"));

    mockMvc.perform(get("/api/approval-workflow/task/task-add-sign/add-sign-targets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value("user-d"))
        .andExpect(jsonPath("$.data[0].label").value("李四"))
        .andExpect(jsonPath("$.data[0].type").value("user"));
    mockMvc.perform(get("/api/approval-workflow/task/task-free-approval/free-approval-targets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value("user-f"))
        .andExpect(jsonPath("$.data[0].label").value("王五"))
        .andExpect(jsonPath("$.data[0].type").value("user"));

    mockMvc.perform(get("/api/approval-workflow/task/task-free-reject/free-reject-targets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value("approve-a"))
        .andExpect(jsonPath("$.data[0].type").value("node"));

    mockMvc.perform(get("/api/approval-workflow/task/task-reject/reject-targets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value("approve-b"))
        .andExpect(jsonPath("$.data[0].type").value("node"));

    mockMvc.perform(get("/api/approval-workflow/task/task-recovery/recovery-targets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value("approve-c"))
        .andExpect(jsonPath("$.data[0].type").value("node"));
  }

  private java.util.Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
    java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<String, Object>();
    map.put(k1, v1);
    map.put(k2, v2);
    return map;
  }

  private FlowTaskDetail taskDetail(String flowKey, String taskDefinitionKey, String instanceId) {
    FlowTaskDetail taskDetail = new FlowTaskDetail();
    taskDetail.setFlowKey(flowKey);
    taskDetail.setTaskDefinitionKey(taskDefinitionKey);
    taskDetail.setInstanceId(instanceId);
    // 动作目标查询会校验归属，fixture 的处理人必须与测试上下文中的当前用户一致
    taskDetail.setAssignee("anonymousUser");
    taskDetail.setCandidateUsers(java.util.Collections.singletonList("anonymousUser"));
    return taskDetail;
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

  private void setField(Object target, String fieldName, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }
}
