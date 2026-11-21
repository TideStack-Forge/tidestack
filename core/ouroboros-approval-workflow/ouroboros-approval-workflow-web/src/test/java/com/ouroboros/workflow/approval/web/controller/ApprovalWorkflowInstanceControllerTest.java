package com.ouroboros.workflow.approval.web.controller;

import com.ouroboros.workflow.approval.FlowStartCommand;
import com.ouroboros.workflow.approval.FlowStartResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.workflow.approval.web.vo.ModelCardResult;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.definition.candidates.UserCandidate;
import com.ouroboros.workflow.approval.definition.candidates.UserSelectCandidate;
import com.ouroboros.workflow.approval.definition.nodes.HandleNode;
import com.ouroboros.workflow.approval.definition.nodes.SingleApprovalNode;
import com.ouroboros.workflow.approval.definition.startrequirements.CandidateRange;
import com.ouroboros.workflow.approval.definition.startrequirements.Requirement;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirements;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirementsResolver;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;
import com.ouroboros.workflow.approval.model.FlowTimeline;
import com.ouroboros.workflow.approval.model.TaskState;
import com.ouroboros.workflow.approval.web.vo.ApprovalWorkflowDiagramResult;
import com.ouroboros.workflow.approval.web.vo.ApprovalWorkflowStartRequirementsResult;
import com.ouroboros.workflow.approval.web.vo.StartFlowInstanceData;
import com.ouroboros.workflow.approval.web.user.CandidateUserResolverRegistry;
import com.ouroboros.workflow.approval.web.user.UsernameResolverRegistry;

class ApprovalWorkflowInstanceControllerTest {

  @Test
  void descriptionOnlyFormProvidesBusinessPageGuidanceWithoutSubmissionSchema() {
    var controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = mock(FlowInstanceService.class);
    when(controller.flowInstanceService.getInitialForm("purchase")).thenReturn(Try.failure(
        new com.ouroboros.workflow.approval.FlowFormServiceUnavailableException("custom")));
    var result = controller.getStartForm("purchase");
    var schema = (SchemaNode) result.getData();
    assertEquals(0, result.getCode());
    assertEquals("alert", schema.getType());
    assertEquals(false, schema.getProp("formAvailable"));
    assertTrue(schema.getProp("body").toString().contains("业务页面"));
    assertNull(schema.getProp("actions"));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(FlowStartResult.Status.class)
  void startHttpPreservesLegacyReferenceAndReturnsActualStartStatus(
      FlowStartResult.Status status) throws Exception {
    var service = mock(FlowInstanceService.class);
    var controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = service;
    when(service.start(org.mockito.ArgumentMatchers.any(FlowStartCommand.class)))
        .thenAnswer(call -> {
          FlowStartCommand command = call.getArgument(0);
          assertEquals("anonymousUser", command.userId());
          assertEquals("request-1", command.idempotencyKey());
          assertEquals("sales.Order", command.instanceVariables().get("businessRecordType"));
          assertEquals("42", command.instanceVariables().get("businessRecordId"));
          assertEquals(List.of("reviewer"), command.taskAssignee().get("review"));
          assertEquals(1200, command.formData().get("amount"));
          return Try.success(new FlowStartResult("execution-1", status,
              "external-1", "https://approval.invalid/1", "fact-1", false));
        });
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/api/approval-workflow/instance/approval/start").contentType("application/json").content("""
            {"userId":"forged-user","businessRecordType":"sales.Order","businessRecordId":"42",
             "idempotencyKey":"request-1","formData":{"amount":1200},
             "taskAssignee":{"review":[{"value":"reviewer"}]}}
            """)).andReturn().getResponse();
    assertEquals(200, response.getStatus());
    JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
    assertEquals(status == FlowStartResult.Status.FAILED ? -1 : 0, body.path("code").asInt());
    assertEquals("execution-1", body.path("data").asText());
    assertEquals(status.name(), body.path("startResult").path("status").asText());
    assertFalse(body.path("startResult").path("taskQueryAvailable").asBoolean());
  }

  @Test
  void instanceInfoHttpReturnsExplicitPresentationCapabilities() throws Exception {
    var controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = mock(FlowInstanceService.class);
    var info = new com.ouroboros.workflow.approval.model.FlowProcessInfo();
    info.setInstanceId("tracking-1");
    info.setStatus("running");
    info.setExternalUrl("https://approval.example/1");
    when(controller.flowInstanceService.getProcessInfo("tracking-1")).thenReturn(Try.success(info));
    var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
    var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get("/api/approval-workflow/instance/tracking-1/info")).andReturn().getResponse();
    JsonNode data = new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(response.getContentAsString()).path("data");
    assertEquals("tracking-1", data.path("instanceId").asText());
    assertEquals("https://approval.example/1", data.path("externalUrl").asText());
    for (String capability : List.of("diagramAvailable", "timelineAvailable", "cancellable", "processable")) {
      assertTrue(data.path(capability).isBoolean(), capability);
      assertFalse(data.path(capability).asBoolean(), capability);
    }
    assertEquals(0, data.path("tasks").size());
  }

  @Test
  void withdrawDelegatesToServiceWithCurrentUserAndComment() {
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    ApprovalWorkflowInstanceController controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = flowInstanceService;
    when(flowInstanceService.withdraw("inst-1", "anonymousUser", "撤回原因")).thenReturn(Try.success(null));

    WebApiResult<Void> result = controller.withdrawInstance("inst-1", Map.of("comment", "撤回原因"));

    assertEquals(0, result.getCode());
    verify(flowInstanceService).withdraw("inst-1", "anonymousUser", "撤回原因");
  }

  @Test
  void withdrawWithoutBodyUsesEmptyComment() {
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    ApprovalWorkflowInstanceController controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = flowInstanceService;
    when(flowInstanceService.withdraw("inst-1", "anonymousUser", "")).thenReturn(Try.success(null));

    WebApiResult<Void> result = controller.withdrawInstance("inst-1", null);

    assertEquals(0, result.getCode());
    verify(flowInstanceService).withdraw("inst-1", "anonymousUser", "");
  }

  @Test
  void getStartFormShouldResolveCandidatesThroughRegistries() {
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    CandidateUserResolverRegistry candidateUserResolverRegistry = mock(CandidateUserResolverRegistry.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    UserCandidate candidate = new UserCandidate();
    candidate.setUsers(Collections.singletonList("user-1"));

    SingleApprovalNode node = new SingleApprovalNode();
    node.setId("approve-1");
    node.setName("经理审批");
    node.setCandidates(Collections.singletonList(candidate));

    SchemaNode formBodyNode = new SchemaNode();
    formBodyNode.setProp("name", "amount");
    ArrayList<SchemaNode> body = new ArrayList<SchemaNode>();
    body.add(formBodyNode);
    SchemaNode formNode = new SchemaNode();
    formNode.setProp("body", body);

    ApprovalWorkflowInstanceController controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = flowInstanceService;
    controller.flowDefinitionService = flowDefinitionService;
    controller.workflowModelService = workflowModelService;
    controller.candidateUserResolverRegistry = candidateUserResolverRegistry;
    controller.usernameResolverRegistry = usernameResolverRegistry;

    when(flowInstanceService.getInitialForm("flow-a")).thenReturn(Try.success(formNode));
    when(flowDefinitionService.getFlowNodes("flow-a")).thenReturn(Collections.singletonList(node));
    when(candidateUserResolverRegistry.resolve(Collections.singletonList(candidate)))
        .thenReturn(Collections.singletonList("user-1"));
    when(usernameResolverRegistry.resolve(Collections.singletonList("user-1")))
        .thenReturn(Collections.singletonMap("user-1", "user-1"));

    WebApiResult<?> result = controller.getStartForm("flow-a");

    assertEquals(0, result.getCode());
    SchemaNode container = (SchemaNode) result.getData();
    List<?> containerBody = (List<?>) container.getProp("body");
    SchemaNode timeline = (SchemaNode) containerBody.get(1);
    List<?> timelineItems = (List<?>) timeline.getProp("items");
    SchemaNode timelineItem = (SchemaNode) timelineItems.get(0);
    SchemaNode title = (SchemaNode) timelineItem.getProp("title");
    assertEquals("user-1", title.getProp("tpl"));

    verify(candidateUserResolverRegistry).resolve(eq(Collections.singletonList(candidate)));
    verify(usernameResolverRegistry).resolve(eq(Collections.singletonList("user-1")));
  }

  @Test
  void startRequirementsShouldReturnDynamicUserSelectSchemaForCurrentConditionContext() {
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    ApprovalWorkflowInstanceController controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = mock(FlowInstanceService.class);
    controller.flowDefinitionService = flowDefinitionService;
    controller.workflowModelService = mock(WorkflowModelService.class);
    controller.candidateUserResolverRegistry = mock(CandidateUserResolverRegistry.class);
    controller.usernameResolverRegistry = mock(UsernameResolverRegistry.class);
    controller.startRequirementsResolver = mock(StartRequirementsResolver.class);

    UserSelectCandidate candidate = new UserSelectCandidate();
    candidate.setMode(UserSelectCandidate.Mode.SINGLE);

    SingleApprovalNode node = new SingleApprovalNode();
    node.setId("approve-1");
    node.setName("经理审批");
    node.setCandidates(Collections.singletonList(candidate));

    Map<String, Object> formData = new LinkedHashMap<>();
    formData.put("amount", 1000);
    StartFlowInstanceData request = new StartFlowInstanceData();
    request.setFormData(formData);

    when(flowDefinitionService.getUserSelectCandidateApprovalNodes(eq("flow-a"), eq(formData)))
        .thenReturn(Collections.singletonList(node));
    when(controller.startRequirementsResolver.getStartRequirements(eq("flow-a"), eq(formData)))
        .thenReturn(Try.success(new StartRequirements(List.of(new Requirement(
            "approve-1", "经理审批", "single", 1, 1, CandidateRange.ROLE, true)), true)));

    WebApiResult<ApprovalWorkflowStartRequirementsResult> result = controller.getStartRequirements("flow-a", request);

    assertEquals(0, result.getCode());
    ApprovalWorkflowStartRequirementsResult requirements = result.getData();
    assertTrue(requirements.isRequiresInput());
    assertNotNull(requirements.getSchema());
    assertEquals("form", requirements.getSchema().getType());
    assertEquals(Boolean.FALSE, requirements.getSchema().getProp(Boolean.class, "wrapWithPanel").orElse(null));

    List<?> body = (List<?>) requirements.getSchema().getProp("body");
    SchemaNode picker = (SchemaNode) body.get(0);
    assertEquals("user-picker", picker.getType());
    assertEquals("taskAssignee.approve-1", picker.getName());
    assertEquals("经理审批", picker.getProp("label"));
    assertEquals(1, picker.getProp("maxLength"));
    assertEquals(Boolean.TRUE, picker.getProp("allowCompanyLevelSelect"));

    // 引擎无关契约字段（Phase 4 M22）：requirements 与 ccEnabled
    assertTrue(requirements.isCcEnabled());
    assertThat(requirements.getRequirements()).singleElement().satisfies(req -> {
      assertEquals("approve-1", req.nodeId());
      assertEquals("single", req.mode());
      assertEquals(1, req.max());
      assertEquals(CandidateRange.ROLE, req.candidateRange());
      assertTrue(req.allowOverride());
    });
  }

  @Test
  void startRequirementsShouldReturnNoInputWhenRuntimeFlowDoesNotRequireAssigneeSelection() {
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    ApprovalWorkflowInstanceController controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = mock(FlowInstanceService.class);
    controller.flowDefinitionService = flowDefinitionService;
    controller.workflowModelService = mock(WorkflowModelService.class);
    controller.candidateUserResolverRegistry = mock(CandidateUserResolverRegistry.class);
    controller.usernameResolverRegistry = mock(UsernameResolverRegistry.class);
    controller.startRequirementsResolver = mock(StartRequirementsResolver.class);

    when(flowDefinitionService.getUserSelectCandidateApprovalNodes("flow-a")).thenReturn(Collections.emptyList());
    when(controller.startRequirementsResolver.getStartRequirements(eq("flow-a"), anyMap()))
        .thenReturn(Try.success(StartRequirements.none(false)));

    WebApiResult<ApprovalWorkflowStartRequirementsResult> result = controller.getStartRequirements("flow-a", null);

    assertEquals(0, result.getCode());
    assertFalse(result.getData().isRequiresInput());
    assertNull(result.getData().getSchema());
    assertThat(result.getData().getRequirements()).isEmpty();
  }

  @Test
  void instanceDiagramReturnsDefinitionNodesEdgesAndActiveNodeIds() {
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    ApprovalWorkflowInstanceController controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = flowInstanceService;
    controller.flowDefinitionService = mock(FlowDefinitionService.class);
    controller.workflowModelService = mock(WorkflowModelService.class);
    controller.candidateUserResolverRegistry = mock(CandidateUserResolverRegistry.class);
    controller.usernameResolverRegistry = mock(UsernameResolverRegistry.class);

    JsonNode definition = com.ouroboros.util.Json.toBean(JsonNode.class, String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-a\",",
        "  \"nodes\": [{\"id\": \"start-a\"}],",
        "  \"edges\": [{\"id\": \"e1\"}]",
        "}"));

    when(flowInstanceService.getInstanceFlowDefinitionV2("instance-1")).thenReturn(Try.success(definition));
    when(flowInstanceService.getActiveActivityIds("instance-1")).thenReturn(Collections.singletonList("start-a"));

    WebApiResult<?> result = controller.instanceDiagram("instance-1");

    assertEquals(0, result.getCode());
    assertTrue(result.getData() instanceof ApprovalWorkflowDiagramResult);
    ApprovalWorkflowDiagramResult data = (ApprovalWorkflowDiagramResult) result.getData();
    assertEquals(definition, data.getDefinition());
    assertEquals(definition.path("nodes"), data.getNodes());
    assertEquals(definition.path("edges"), data.getEdges());
    assertEquals(Collections.singletonList("start-a"), data.getActiveNodeIds());
  }

  @Test
  void instanceTimelineShowsHandledStatusForHandleNode() {
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);
    ApprovalWorkflowInstanceController controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = flowInstanceService;
    controller.flowDefinitionService = mock(FlowDefinitionService.class);
    controller.workflowModelService = mock(WorkflowModelService.class);
    controller.candidateUserResolverRegistry = mock(CandidateUserResolverRegistry.class);
    controller.usernameResolverRegistry = usernameResolverRegistry;

    HandleNode node = new HandleNode();
    node.setId("handle-a");
    node.setName("资料办理");

    FlowTimeline.TaskInstance task = new FlowTimeline.TaskInstance();
    task.setAssignee("user-a");
    task.setCandidateUsers(Collections.<String>emptyList());
    task.setState(TaskState.HANDLED);

    FlowTimeline timeline = new FlowTimeline();
    timeline.setFlowNode(node);
    timeline.setState(TaskState.HANDLED);
    timeline.setTaskInstances(Collections.singletonList(task));

    when(flowInstanceService.getTimeLine("instance-1")).thenReturn(Collections.singletonList(timeline));
    when(usernameResolverRegistry.resolve(Collections.singletonList("user-a")))
        .thenReturn(Collections.singletonMap("user-a", "张三"));

    WebApiResult<?> result = controller.instanceTimeline("instance-1");

    SchemaNode data = (SchemaNode) result.getData();
    assertEquals("workflow-approval-timeline", data.getProp("className"));
    List<?> items = (List<?>) data.getProp("items");
    SchemaNode item = (SchemaNode) items.get(0);
    assertTrue(((String) item.getProp("timeClassName")).contains("text-xs"));
    SchemaNode title = (SchemaNode) item.getProp("title");
    List<?> body = (List<?>) title.getProp("body");
    assertEquals("success", item.getProp("icon"));
    assertTrue(body.stream()
        .map(SchemaNode.class::cast)
        .anyMatch(text -> "人员：张三".equals(text.getProp("value"))));
    assertTrue(body.stream()
        .map(SchemaNode.class::cast)
        .anyMatch(text -> "已办理".equals(text.getProp("value"))));
    String timelineText = body.stream()
        .map(SchemaNode.class::cast)
        .map(text -> String.valueOf(text.getProp("value")))
        .collect(java.util.stream.Collectors.joining("\n"));
    assertFalse(timelineText.contains("节点："));
    assertFalse(timelineText.contains("资料办理"));
  }

  @Test
  void instanceTimelineSuppressesTechnicalNodeNames() {
    FlowInstanceService flowInstanceService = mock(FlowInstanceService.class);
    UsernameResolverRegistry usernameResolverRegistry = mock(UsernameResolverRegistry.class);
    ApprovalWorkflowInstanceController controller = new ApprovalWorkflowInstanceController();
    controller.flowInstanceService = flowInstanceService;
    controller.flowDefinitionService = mock(FlowDefinitionService.class);
    controller.workflowModelService = mock(WorkflowModelService.class);
    controller.candidateUserResolverRegistry = mock(CandidateUserResolverRegistry.class);
    controller.usernameResolverRegistry = usernameResolverRegistry;

    HandleNode node = new HandleNode();
    node.setId("approval-a");
    node.setName("ApprovalNode");

    FlowTimeline.TaskInstance task = new FlowTimeline.TaskInstance();
    task.setAssignee("approver-a");
    task.setState(TaskState.PROCESSING);

    FlowTimeline timeline = new FlowTimeline();
    timeline.setFlowNode(node);
    timeline.setState(TaskState.PROCESSING);
    timeline.setTaskInstances(Collections.singletonList(task));

    when(flowInstanceService.getTimeLine("instance-1")).thenReturn(Collections.singletonList(timeline));
    when(usernameResolverRegistry.resolve(Collections.singletonList("approver-a")))
        .thenReturn(Collections.singletonMap("approver-a", "李四"));

    WebApiResult<?> result = controller.instanceTimeline("instance-1");

    SchemaNode data = (SchemaNode) result.getData();
    List<?> items = (List<?>) data.getProp("items");
    SchemaNode item = (SchemaNode) items.get(0);
    SchemaNode title = (SchemaNode) item.getProp("title");
    List<?> body = (List<?>) title.getProp("body");
    String timelineText = body.stream()
        .map(SchemaNode.class::cast)
        .map(text -> String.valueOf(text.getProp("value")))
        .collect(java.util.stream.Collectors.joining("\n"));
    assertTrue(timelineText.contains("人员：李四"));
    assertFalse(timelineText.contains("节点："));
    assertFalse(timelineText.contains("ApprovalNode"));
    assertFalse(timelineText.contains("StartNode"));
  }

  /**
   * 可发起流程列表：一次返回全部分类分组，不因入参分页而截断；total 是"可发起流程总数"。
   *
   * <p>此前分页发生在按分类聚合之前、且 {@code total} 取"本页聚合出的分类数"，
   * 会导致同一分类跨页重复/丢失、且 total 口径与 items 不一致。
   * 前端 schema（approval-center/start.ui-schema.json）以 {@code each → items → cards}
   * 消费该接口且请求 page=1&perPage=1000，所以契约就是"一次给全"。
   */
  @Test
  void getAvailableReturnsAllCategoryGroupsRegardlessOfPagingParams() {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowInstanceController controller = new ApprovalWorkflowInstanceController();
    controller.workflowModelService = workflowModelService;

    WorkflowModel first = availableModel("flow-a", "人事审批", "请假申请");
    WorkflowModel second = availableModel("flow-b", "人事审批", "调岗申请");
    WorkflowModel third = availableModel("flow-c", "财务审批", "报销申请");
    // 服务层按 (1, 1000) 返回全部三条，total 为 3
    when(workflowModelService.searchAvailableByPage(null, null, 1, 1000))
        .thenReturn(PagedWebApiResult.ok(3L, Arrays.asList(first, second, third)));

    PagedWebApiResult<ModelCardResult> result = controller.getAvailable(2, 1);

    // 入参 page=2&perPage=1 不应造成截断：三个流程、两个分组都要在
    assertEquals(3L, result.getData().getTotal());
    List<ModelCardResult> groups = result.getData().getItems();
    assertEquals(2, groups.size());
    assertEquals("人事审批", groups.get(0).getTitle());
    assertEquals(2, groups.get(0).getCards().size());
    assertEquals("财务审批", groups.get(1).getTitle());
    assertEquals(1, groups.get(1).getCards().size());
    // 取数必须一次拿全，不能按入参分页
    verify(workflowModelService).searchAvailableByPage(null, null, 1, 1000);
  }

  private WorkflowModel availableModel(String id, String category, String name) {
    WorkflowModel model = new WorkflowModel();
    model.setId(id);
    model.setCategory(category);
    model.setName(name);
    return model;
  }
}
