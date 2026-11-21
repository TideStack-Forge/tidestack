package com.ouroboros.workflow.approval.logicflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.context.support.GenericApplicationContext;

import io.vavr.control.Try;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.results.Decision;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowTaskService;
import com.ouroboros.workflow.approval.model.FlowProcessInfo;
import com.ouroboros.workflow.approval.model.FlowTaskDetail;

class ApprovalWorkflowLogicFlowNodeTest {
  private GenericApplicationContext context;
  private FlowInstanceService flowInstanceService;
  private FlowTaskService flowTaskService;
  private final SpringContextUtil bridge = new SpringContextUtil();
  private com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService cases;

  @BeforeEach
  void setUp() {
    flowInstanceService = mock(FlowInstanceService.class, Answers.CALLS_REAL_METHODS);
    flowTaskService = mock(FlowTaskService.class);
    cases = mock(com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService.class);
    context = new GenericApplicationContext();
    context.registerBean(FlowInstanceService.class, () -> flowInstanceService);
    context.registerBean(FlowTaskService.class, () -> flowTaskService);
    context.registerBean(com.ouroboros.workflow.approval.lifecycle.ApprovalCaseService.class, () -> cases);
    context.refresh();
    bridge.setApplicationContext(context);
  }

  @AfterEach
  void tearDown() {
    if (context != null) {
      context.close();
    }
    bridge.destroy();
  }

  @Test
  void boundStartNormalizesNumericIdentityAtTheOfficialEngineVariableBoundary() {
    var node = new StartProcessNode(flowModel(), FlowNodeMeta.builder().name("发起流程").type("startProcess")
        .extraProp("processId", "flow-a").build());
    when(flowInstanceService.start(eq("flow-a"), eq("anonymousUser"), any(), any(), any(), any(CompletableFuture.class)))
        .thenAnswer(call -> {
          Map<String, Object> variables = call.getArgument(3);
          assertEquals("42", variables.get("businessRecordId"));
          assertFalse(variables.containsKey("approvalCaseId"));
          assertFalse(variables.containsKey("submissionRound"));
          return Try.success("instance-1");
        });
    assertEquals("instance-1", node.execute(List.of(Map.of("businessRecordType", "sales.Order", "businessRecordId", 42))).get().getFirst());
    verifyNoInteractions(cases);
  }

  @Test
  void startProcessNodeShouldDelegateToFlowInstanceServiceWithTemplateVariables() {
    FlowNodeMeta meta = FlowNodeMeta.builder()
        .name("发起流程")
        .type("startProcess")
        .extraProp("processId", "flow-a")
        .build();
    StartProcessNode node = new StartProcessNode(flowModel(), meta);
    Map<String, Object> formData = new LinkedHashMap<>();
    formData.put("amount", 1200);
    Map<String, Object> taskAssignee = new LinkedHashMap<>();
    taskAssignee.put("approve-a", List.of(Collections.singletonMap("value", "user-a")));
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("initiator", "starter-1");
    input.put("formData", formData);
    input.put("taskAssignee", taskAssignee);
    input.put("callbackUrl", "/api/demo/customer/1/process-callback");
    input.put("approvalUrl", "/demo/customer/1/approval");

    when(flowInstanceService.start(
        eq("flow-a"),
        eq("starter-1"),
        eq(Collections.singletonMap("approve-a", List.of("user-a"))),
        argThat(variables -> !variables.containsKey("callbackUrl")
            && "/demo/customer/1/approval".equals(variables.get("approvalUrl"))
            && !variables.containsKey("formData")
            && !variables.containsKey("taskAssignee")),
        eq(formData),
        any(CompletableFuture.class)))
        .thenReturn(Try.success("instance-1"));

    Object result = node.execute(Collections.singletonList(input)).get().get(0);

    assertEquals("instance-1", result);
  }

  @Test
  void startNodePassesOptionsToTheUnifiedContractAndKeepsTheLegacyExecutionReference() {
    var node = new StartProcessNode(flowModel(), FlowNodeMeta.builder().name("发起流程").type("startProcess")
        .extraProp("processId", "flow-a").build());
    var cc = Map.<String, Object>of("userIds", List.of("cc-user"), "position", "START");
    org.mockito.Mockito.doAnswer(call -> {
      com.ouroboros.workflow.approval.FlowStartCommand command = call.getArgument(0);
      assertEquals("request-1", command.idempotencyKey());
      assertEquals(cc, command.cc());
      assertEquals(List.of("reviewer"), command.approvers().get("review"));
      assertEquals("sales.Order.callback", command.instanceVariables().get("callbackFlow"));
      assertFalse(command.instanceVariables().containsKey("cc"));
      return Try.success(new com.ouroboros.workflow.approval.FlowStartResult("execution-1",
          com.ouroboros.workflow.approval.FlowStartResult.Status.START_UNKNOWN, null, null, "fact-1", false));
    }).when(flowInstanceService).start(any(com.ouroboros.workflow.approval.FlowStartCommand.class));
    assertEquals("execution-1", node.execute(List.of(Map.of("businessRecordType", "sales.Order", "businessRecordId", "42",
        "idempotencyKey", "request-1", "callbackFlow", "sales.Order.callback", "cc", cc,
        "approvers", Map.of("review", List.of("reviewer"))))).get().getFirst());
  }

  @Test
  void submitTaskNodeShouldMapLegacyPassTokenToApproveAction() {
    FlowNodeMeta meta = FlowNodeMeta.builder()
        .name("提交工作流任务")
        .type("submitTask")
        .build();
    SubmitTaskNode node = new SubmitTaskNode(flowModel(), meta);
    Map<String, Object> formData = new LinkedHashMap<>();
    formData.put("amount", 1200);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("taskId", "task-1");
    input.put("action", "PASS");
    input.put("comment", "approved");
    input.put("formData", formData);
    when(flowTaskService.doAction(
        eq("task-1"),
        eq("approve"),
        eq("anonymousUser"),
        eq("approved"),
        eq(formData),
        isNull()))
        .thenReturn(Try.success(null));

    assertTrue(node.execute(Collections.singletonList(input)).isSuccess());

    verify(flowTaskService).doAction("task-1", "approve", "anonymousUser", "approved", formData, null);
  }

  @Test
  void getTaskInfoNodeShouldReturnTaskDetailMap() {
    FlowNodeMeta meta = FlowNodeMeta.builder()
        .name("获取任务信息")
        .type("getTaskInfo")
        .build();
    GetTaskInfoNode node = new GetTaskInfoNode(flowModel(), meta);
    FlowTaskDetail detail = new FlowTaskDetail();
    detail.setId("task-1");
    detail.setName("经理审批");
    detail.setInstanceId("instance-1");
    detail.setFlowKey("flow-a");
    detail.setFlowName("费用审批");
    detail.setTaskDefinitionKey("approve-a");
    detail.setCandidateUsers(List.of("user-a"));
    detail.setStarter("starter-1");
    detail.setActive(true);
    when(flowTaskService.getTaskDetail("task-1")).thenReturn(detail);

    Map<String, Object> input = Collections.singletonMap("taskId", "task-1");
    Object result = node.execute(Collections.singletonList(input)).get().get(0);

    assertTrue(result instanceof Map);
    Map<?, ?> map = (Map<?, ?>) result;
    assertEquals("task-1", map.get("taskId"));
    assertEquals("经理审批", map.get("taskName"));
    assertEquals("instance-1", map.get("instanceId"));
    assertEquals("approve-a", map.get("type"));
  }

  @Test
  void attachProcessInfoNodeShouldAppendProcessInfoToPagedItems() {
    FlowNodeMeta meta = FlowNodeMeta.builder()
        .name("附加工作流信息")
        .type("attachProcessInfo")
        .extraProp("processId", "flow-a")
        .extraProp("modelPath", "/demo/customer")
        .build();
    AttachProcessInfoNode node = new AttachProcessInfoNode(flowModel(), meta);
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", "1");
    record.put("name", "customer-a");
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("items", Collections.singletonList(record));

    FlowProcessInfo processInfo = new FlowProcessInfo();
    processInfo.setInstanceId("instance-1");
    processInfo.setFlowKey("flow-a");
    processInfo.setStatus("running");
    processInfo.setProcessable(true);
    processInfo.setProcessableTaskId("task-1");
    FlowProcessInfo.TaskInfo taskInfo = new FlowProcessInfo.TaskInfo();
    taskInfo.setTaskId("task-1");
    taskInfo.setTaskDefinitionKey("approve-a");
    taskInfo.setNodeType("审批节点");
    taskInfo.setActive(true);
    processInfo.setTasks(Collections.singletonList(taskInfo));

    doReturn(io.vavr.control.Try.success(
            Collections.singletonMap("/demo/customer/1/approval", processInfo)))
        .when(flowInstanceService)
        .getProcessInfoByApprovalUrls(
            eq("flow-a"),
            eq(Collections.singletonList("/demo/customer/1/approval")));

    Object result = node.execute(Collections.singletonList(input)).get().get(0);

    assertTrue(result instanceof Map);
    Map<?, ?> resultMap = (Map<?, ?>) result;
    List<?> items = (List<?>) resultMap.get("items");
    Map<?, ?> resultRecord = (Map<?, ?>) items.get(0);
    Map<?, ?> attachedProcessInfo = (Map<?, ?>) resultRecord.get("processInfo");
    List<?> tasks = (List<?>) attachedProcessInfo.get("tasks");
    Map<?, ?> attachedTask = (Map<?, ?>) tasks.get(0);
    assertEquals("instance-1", attachedProcessInfo.get("instanceId"));
    assertEquals(Boolean.TRUE, attachedProcessInfo.get("processable"));
    assertEquals("task-1", attachedProcessInfo.get("processableTaskId"));
    assertEquals("task-1", attachedTask.get("taskId"));
    assertEquals("审批节点", attachedTask.get("nodeType"));
  }

  @Test
  void typedProcessInfoNodeKeepsResultOnlyLinkWithoutInventingTaskActions() {
    var meta = FlowNodeMeta.builder().name("附加工作流信息").type("attachProcessInfo")
        .extraProp("processId", "flow-a").extraProp("modelPath", "/demo/customer")
        .extraProp("businessRecordType", "demo.Customer").build();
    var info = new FlowProcessInfo();
    info.setInstanceId("tracking-1");
    info.setExternalUrl("https://approval.example/1");
    doReturn(io.vavr.control.Try.success(Map.of("/demo/customer/1/approval", info)))
        .when(flowInstanceService).getProcessInfoByApprovalUrls("flow-a",
            List.of("/demo/customer/1/approval"), "demo.Customer");

    var result = (List<?>) new AttachProcessInfoNode(flowModel(), meta)
        .execute(List.of(List.of(Map.of("id", "1")))).get().get(0);
    var process = (Map<?, ?>) ((Map<?, ?>) result.get(0)).get("processInfo");
    assertEquals("tracking-1", process.get("instanceId"));
    assertEquals("https://approval.example/1", process.get("externalUrl"));
    assertEquals(false, process.get("processable"));
    assertEquals(false, process.get("diagramAvailable"));
    assertEquals(false, process.get("timelineAvailable"));
    assertEquals(List.of(), process.get("tasks"));
  }

  @Test
  void processStatusRouterNodeShouldSelectOutcomeBranch() {
    FlowNodeMeta meta = FlowNodeMeta.builder()
        .name("流程状态路由")
        .type("processStatusRouter")
        .build();
    ProcessStatusRouterNode node = new ProcessStatusRouterNode(flowModel(), meta);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("eventType", "APPROVED");
    input.put("outcome", "approved");

    List<Object> result = node.execute(Collections.singletonList(input)).get();

    assertDecision(result, 3, input);
  }

  @Test
  void processStatusRouterNodeShouldUseConfiguredStatusCodeMap() {
    Map<String, Object> approved = new LinkedHashMap<>();
    approved.put("code", "APPROVED_CODE");
    Map<String, Object> statusMap = new LinkedHashMap<>();
    statusMap.put("approved", approved);
    FlowNodeMeta meta = FlowNodeMeta.builder()
        .name("流程状态路由")
        .type("processStatusRouter")
        .extraProp("statusField", "approvalStatus")
        .extraProp("statusMap", statusMap)
        .build();
    ProcessStatusRouterNode node = new ProcessStatusRouterNode(flowModel(), meta);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("approvalStatus", Collections.singletonMap("code", "APPROVED_CODE"));

    List<Object> result = node.execute(Collections.singletonList(input)).get();

    assertDecision(result, 3, input);
  }

  @Test
  void processStatusRouterNodeShouldRouteSuspendedAndWithdrawnStates() {
    FlowNodeMeta meta = FlowNodeMeta.builder()
        .name("流程状态路由")
        .type("processStatusRouter")
        .build();
    ProcessStatusRouterNode node = new ProcessStatusRouterNode(flowModel(), meta);
    Map<String, Object> suspended = Collections.singletonMap("status", "suspended");
    Map<String, Object> withdrawn = Collections.singletonMap("outcome", "withdrawn");

    assertDecision(node.execute(Collections.singletonList(suspended)).get(), 1, suspended);
    assertDecision(node.execute(Collections.singletonList(withdrawn)).get(), 2, withdrawn);
  }

  @Test
  void processStatusRouterNodeShouldRouteUnknownStatusToDefaultBranch() {
    FlowNodeMeta meta = FlowNodeMeta.builder()
        .name("流程状态路由")
        .type("processStatusRouter")
        .build();
    ProcessStatusRouterNode node = new ProcessStatusRouterNode(flowModel(), meta);
    Map<String, Object> input = Collections.singletonMap("status", "finished");

    List<Object> result = node.execute(Collections.singletonList(input)).get();

    assertDecision(result, 7, input);
  }

  private void assertDecision(List<Object> result, int selectedIndex, Object value) {
    assertEquals(8, result.size());
    for (int index = 0; index < result.size(); index++) {
      Decision decision = (Decision) result.get(index);
      assertEquals(index == selectedIndex, decision.isSatisfied());
      if (index == selectedIndex) {
        assertEquals(value, decision.getValue());
      }
    }
  }

  private FlowModel flowModel() {
    FlowModel model = mock(FlowModel.class);
    when(model.getConnections()).thenReturn(Collections.emptyList());
    return model;
  }
}
