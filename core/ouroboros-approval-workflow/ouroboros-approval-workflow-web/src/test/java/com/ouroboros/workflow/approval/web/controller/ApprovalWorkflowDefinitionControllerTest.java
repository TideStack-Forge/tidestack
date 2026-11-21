package com.ouroboros.workflow.approval.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ouroboros.util.Json;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.workflow.approval.FlowDefinitionService;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;
import com.ouroboros.workflow.approval.model.FlowDefinitionVersion;
import com.ouroboros.workflow.approval.web.vo.ApprovalWorkflowDiagramResult;
import com.ouroboros.workflow.approval.web.vo.WorkflowDefinitionVersionDetailResult;
import com.ouroboros.workflow.approval.web.vo.WorkflowDefinitionVersionResult;
import com.ouroboros.workflow.approval.web.vo.WorkflowModelResult;

import io.vavr.control.Try;

public class ApprovalWorkflowDefinitionControllerTest {
  @Test
  void createConvertsLegacyV1ToStaticMetadataBeforePersisting() throws Exception {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;

    when(workflowModelService.add(org.mockito.ArgumentMatchers.<WorkflowModel>any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    WebApiResult<Void> result = controller.create(v1FlowJson());

    ArgumentCaptor<WorkflowModel> captor = ArgumentCaptor.forClass(WorkflowModel.class);
    verify(workflowModelService).add(captor.capture());

    WorkflowModel workflowModel = captor.getValue();
    JsonNode persisted = Json.toBean(JsonNode.class, workflowModel.getJson());
    assertEquals(0, result.getCode());
    assertEquals(1, persisted.path("formatVersion").asInt());
    assertTrue(persisted.path("edges").isArray());
    assertEquals("flow-a", persisted.path("name").asText());
    assertEquals("采购审批", persisted.path("title").asText());
    assertEquals("purchase-form", persisted.path("form").path("formId").asText());
    assertFalse(persisted.has("id"));
    assertFalse(persisted.has("formKey"));
    assertEquals("flow-a", workflowModel.getId());
    assertEquals("采购审批", workflowModel.getName());
    // 首次创建补齐实体非空字段；更新时仍保留维护者设置的图标和分类。
    assertEquals("", workflowModel.getIcon());
    assertEquals("", workflowModel.getCategory());
    assertEquals("", workflowModel.getProcessDefinitionId());
    assertEquals(WorkflowModel.SOURCE_INSTANCE, workflowModel.getSource());
    assertEquals(Integer.valueOf(1), workflowModel.getVersion());
    assertFalse(workflowModel.getAdopted());
  }

  @Test
  void updateConvertsLegacyV1ToStaticMetadataBeforePersisting() {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;

    WorkflowModel existing = new WorkflowModel();
    existing.setId("flow-a");
    existing.setStatus("draft");
    when(workflowModelService.getByPrimaryKey("flow-a")).thenReturn(existing);

    WebApiResult<Void> result = controller.update("flow-a", v1FlowJson());

    ArgumentCaptor<WorkflowModel> captor = ArgumentCaptor.forClass(WorkflowModel.class);
    verify(workflowModelService).update(eq("flow-a"), captor.capture());

    WorkflowModel workflowModel = captor.getValue();
    JsonNode persisted = Json.toBean(JsonNode.class, workflowModel.getJson());
    assertEquals(0, result.getCode());
    assertEquals(1, persisted.path("formatVersion").asInt());
    assertTrue(persisted.path("edges").isArray());
    assertEquals("flow-a", persisted.path("name").asText());
    assertEquals("采购审批", persisted.path("title").asText());
    assertEquals("flow-a", workflowModel.getId());
    assertEquals("采购审批", workflowModel.getName());
  }

  @Test
  void createConvertsNativeV2ToStaticMetadataAndPreservesEditorLayout() throws Exception {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;

    when(workflowModelService.add(org.mockito.ArgumentMatchers.<WorkflowModel>any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    WebApiResult<Void> result = controller.create(v2FlowJsonWithEditorMeta());

    ArgumentCaptor<WorkflowModel> captor = ArgumentCaptor.forClass(WorkflowModel.class);
    verify(workflowModelService).add(captor.capture());

    JsonNode persisted = Json.toBean(JsonNode.class, captor.getValue().getJson());
    assertEquals(0, result.getCode());
    assertCanonicalStaticMetadata(persisted);
  }

  @Test
  void updateConvertsNativeV2ToStaticMetadataAndPreservesEditorLayout() {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;

    WorkflowModel existing = new WorkflowModel();
    existing.setId("flow-a");
    existing.setStatus("draft");
    when(workflowModelService.getByPrimaryKey("flow-a")).thenReturn(existing);

    WebApiResult<Void> result = controller.update("flow-a", v2FlowJsonWithEditorMeta());

    ArgumentCaptor<WorkflowModel> captor = ArgumentCaptor.forClass(WorkflowModel.class);
    verify(workflowModelService).update(eq("flow-a"), captor.capture());

    JsonNode persisted = Json.toBean(JsonNode.class, captor.getValue().getJson());
    assertEquals(0, result.getCode());
    assertCanonicalStaticMetadata(persisted);
  }

  @Test
  void updateDeployedDefinitionOnlySavesAndMarksUpdated() {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;

    WorkflowModel existing = new WorkflowModel();
    existing.setId("flow-a");
    existing.setStatus("deployed");
    existing.setProcessDefinitionId("flow-a:1:100");
    when(workflowModelService.getByPrimaryKey("flow-a")).thenReturn(existing);

    WebApiResult<Void> result = controller.update("flow-a", v2FlowJsonWithEditorMeta());

    ArgumentCaptor<WorkflowModel> captor = ArgumentCaptor.forClass(WorkflowModel.class);
    verify(workflowModelService).update(eq("flow-a"), captor.capture());
    verify(workflowModelService, never()).deploy(anyString());

    assertEquals(0, result.getCode());
    assertEquals("updated", captor.getValue().getStatus());
    assertEquals("flow-a:1:100", captor.getValue().getProcessDefinitionId());
  }

  @Test
  void updateRejectsDefinitionIdMismatchBetweenPathAndBody() {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;

    WorkflowModel existing = new WorkflowModel();
    existing.setId("flow-a");
    existing.setStatus("draft");
    when(workflowModelService.getByPrimaryKey("flow-a")).thenReturn(existing);

    WebApiResult<Void> result = controller.update("flow-a", v2FlowJsonWithId("flow-b"));

    assertNotEquals(0, result.getCode());
    assertTrue(result.getMsg() != null && !result.getMsg().isEmpty());
    verify(workflowModelService, never()).update(eq("flow-a"), any(WorkflowModel.class));
    verify(workflowModelService, never()).add(any(WorkflowModel.class));
    verify(workflowModelService, never()).deploy(anyString());
  }

  @Test
  void resetDelegatesToWorkflowModelService() {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;

    WebApiResult<Void> result = controller.reset("flow-a");

    assertEquals(0, result.getCode());
    verify(workflowModelService).reset("flow-a");
  }

  @Test
  void resetMapsServiceFailureToResultCode() {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;
    doThrow(new RuntimeException("重置失败")).when(workflowModelService).reset("flow-a");

    WebApiResult<Void> result = controller.reset("flow-a");

    assertNotEquals(0, result.getCode());
    verify(workflowModelService).reset("flow-a");
  }

  @Test
  void getDefaultsToStaticMetadataWhenStoredDocumentIsLegacyV1() throws Exception {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;

    WorkflowModel workflowModel = new WorkflowModel();
    workflowModel.setId("flow-a");
    workflowModel.setJson(v1FlowJson());
    when(workflowModelService.getByPrimaryKey("flow-a")).thenReturn(workflowModel);

    WebApiResult<Object> defaultResult = controller.get("flow-a", null);
    WebApiResult<Object> v2Result = controller.get("flow-a", 2);

    assertEquals(0, defaultResult.getCode());
    assertInstanceOf(JsonNode.class, defaultResult.getData());
    assertEquals(1, ((JsonNode) defaultResult.getData()).path("formatVersion").asInt());

    assertEquals(0, v2Result.getCode());
    assertInstanceOf(JsonNode.class, v2Result.getData());
    assertEquals(defaultResult.getData(), v2Result.getData());
  }

  @Test
  void getIgnoresLegacyFormatVersionParameterAndReturnsStaticMetadata() throws Exception {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;

    WorkflowModel workflowModel = new WorkflowModel();
    workflowModel.setId("flow-a");
    workflowModel.setJson(v1FlowJson());
    when(workflowModelService.getByPrimaryKey("flow-a")).thenReturn(workflowModel);

    WebApiResult<Object> result = controller.get("flow-a", 1);

    assertEquals(0, result.getCode());
    assertInstanceOf(JsonNode.class, result.getData());
    assertEquals(1, ((JsonNode) result.getData()).path("formatVersion").asInt());
  }

  @Test
  void getDefaultReadReturnsStaticMetadataAndPreservesEditorLayout() throws Exception {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;

    WorkflowModel workflowModel = new WorkflowModel();
    workflowModel.setId("flow-a");
    workflowModel.setJson(v2FlowJsonWithEditorMeta());
    when(workflowModelService.getByPrimaryKey("flow-a")).thenReturn(workflowModel);

    WebApiResult<Object> defaultResult = controller.get("flow-a", null);

    assertEquals(0, defaultResult.getCode());
    assertInstanceOf(JsonNode.class, defaultResult.getData());
    JsonNode root = (JsonNode) defaultResult.getData();
    assertCanonicalStaticMetadata(root);
  }

  @Test
  void searchFallsBackToModelSummaryWhenStoredDefinitionPayloadInvalid() throws Exception {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;

    WorkflowModel workflowModel = new WorkflowModel();
    workflowModel.setId("flow-a");
    workflowModel.setName("采购审批");
    workflowModel.setStatus("draft");
    workflowModel.setDescription("legacy invalid payload");
    workflowModel.setVersion(0);
    workflowModel.setJson("{\"formatVersion\":2,\"id\":\"flow-a\"}");
    when(workflowModelService.searchByPage(null, null, 1, 20))
        .thenReturn(PagedWebApiResult.ok(1, List.of(workflowModel)));

    PagedWebApiResult<WorkflowModelResult> result = controller.search(null, "all", 1, 20);

    assertEquals(0, result.getCode());
    assertEquals(1, result.getData().getItems().size());
    WorkflowModelResult item = result.getData().getItems().get(0);
    assertEquals("flow-a", item.getName());
    assertEquals("采购审批", item.getTitle());
    assertEquals("legacy invalid payload", item.getDescription());
    assertEquals(Integer.valueOf(0), item.getVersion());
    assertEquals("draft", item.getStatus());
  }

  @Test
  void getDefinitionDiagramReturnsDefinitionNodesAndEdges() {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;
    controller.flowDefinitionService = flowDefinitionService;

    JsonNode definition = Json.toBean(JsonNode.class, v2FlowJsonWithEditorMeta());
    when(flowDefinitionService.getFlowDefinitionV2("flow-a", 3)).thenReturn(Try.success(definition));

    WebApiResult<?> result = controller.getDefinitionDiagram("flow-a", 3);

    assertEquals(0, result.getCode());
    assertTrue(result.getData() instanceof ApprovalWorkflowDiagramResult);
    ApprovalWorkflowDiagramResult data = (ApprovalWorkflowDiagramResult) result.getData();
    assertEquals(definition, data.getDefinition());
    assertEquals(definition.path("nodes"), data.getNodes());
    assertEquals(definition.path("edges"), data.getEdges());
  }

  @Test
  void listVersionsReturnsContractFields() {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;
    controller.flowDefinitionService = flowDefinitionService;

    FlowDefinitionVersion version = new FlowDefinitionVersion();
    version.setVersion(1);
    version.setProcessDefinitionId("flow-a:1:100");
    version.setDeploymentId("deployment-100");
    version.setDeploymentTime(LocalDateTime.of(2026, 4, 27, 10, 30));
    version.setDeployedBy("alice");
    FlowDefinitionVersion latestVersion = new FlowDefinitionVersion();
    latestVersion.setVersion(2);
    latestVersion.setProcessDefinitionId("flow-a:2:200");
    latestVersion.setDeploymentId("deployment-200");
    latestVersion.setDeploymentTime(LocalDateTime.of(2026, 4, 27, 11, 30));
    latestVersion.setDeployedBy("bob");
    when(flowDefinitionService.listFlowDefinitionVersions("flow-a"))
        .thenReturn(Try.success(Arrays.asList(latestVersion, version)));

    WebApiResult<List<WorkflowDefinitionVersionResult>> result = controller.listVersions("flow-a");

    assertEquals(0, result.getCode());
    assertNotNull(result.getData());
    assertEquals(2, result.getData().size());
    WorkflowDefinitionVersionResult first = result.getData().get(0);
    assertEquals("flow-a", first.getFlowKey());
    assertEquals(2, first.getVersion());
    assertEquals("flow-a:2:200", first.getProcessDefinitionId());
    assertEquals("deployment-200", first.getDeploymentId());
    assertEquals(LocalDateTime.of(2026, 4, 27, 11, 30), first.getDeploymentTime());
    assertEquals("bob", first.getDeployedBy());
    assertEquals("active", first.getStatus());
    WorkflowDefinitionVersionResult second = result.getData().get(1);
    assertEquals("flow-a", second.getFlowKey());
    assertEquals("inactive", second.getStatus());
  }

  @Test
  void getVersionReturnsPageMetadataContractFields() {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;
    controller.flowDefinitionService = flowDefinitionService;

    FlowDefinitionVersion version = new FlowDefinitionVersion();
    version.setVersion(2);
    version.setProcessDefinitionId("flow-a:2:200");
    version.setDeploymentId("deployment-200");
    version.setDeploymentTime(LocalDateTime.of(2026, 4, 27, 11, 30));
    version.setDeployedBy("bob");
    version.setDefinition(v2FlowJsonWithEditorMeta());
    when(flowDefinitionService.getFlowDefinitionVersion("flow-a", 2)).thenReturn(Try.success(version));
    when(flowDefinitionService.listFlowDefinitionVersions("flow-a"))
        .thenReturn(Try.success(java.util.Collections.singletonList(version)));

    WebApiResult<WorkflowDefinitionVersionDetailResult> result = controller.getVersion("flow-a", 2);

    assertEquals(0, result.getCode());
    WorkflowDefinitionVersionDetailResult data = result.getData();
    assertEquals("flow-a", data.getFlowKey());
    assertEquals(2, data.getVersion());
    assertEquals("采购审批", data.getName());
    assertEquals("", data.getCategory());
    assertEquals("", data.getDescription());
    assertEquals(LocalDateTime.of(2026, 4, 27, 11, 30), data.getDeploymentTime());
    assertEquals("active", data.getStatus());
    assertEquals("flow-a:2:200", data.getProcessDefinitionId());
    assertEquals("deployment-200", data.getDeploymentId());
    assertEquals("bob", data.getDeployedBy());
    assertInstanceOf(JsonNode.class, data.getDefinition());
    JsonNode definition = (JsonNode) data.getDefinition();
    assertEquals(1, definition.path("formatVersion").asInt());
    assertEquals("flow-a", definition.path("name").asText());
    assertEquals("采购审批", definition.path("title").asText());
  }

  @Test
  void getVersionReturnsFailureWhenDefinitionPayloadInvalid() {
    WorkflowModelService workflowModelService = mock(WorkflowModelService.class);
    FlowDefinitionService flowDefinitionService = mock(FlowDefinitionService.class);
    ApprovalWorkflowDefinitionController controller = new ApprovalWorkflowDefinitionController();
    controller.workflowModelService = workflowModelService;
    controller.flowDefinitionService = flowDefinitionService;

    FlowDefinitionVersion version = new FlowDefinitionVersion();
    version.setVersion(2);
    version.setProcessDefinitionId("flow-a:2:200");
    version.setDefinition("{\"formatVersion\":2,\"id\":\"flow-a\"}");
    when(flowDefinitionService.getFlowDefinitionVersion(eq("flow-a"), org.mockito.ArgumentMatchers.anyInt())).thenReturn(Try.success(version));

    WebApiResult<WorkflowDefinitionVersionDetailResult> result = controller.getVersion("flow-a", 2);

    assertNotEquals(0, result.getCode());
    assertTrue(result.getMsg() != null && !result.getMsg().isEmpty());
  }

  private String v1FlowJson() {
    return String.join("\n",
        "{",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"category\": \"\",",
        "  \"icon\": \"shenpi\",",
        "  \"description\": \"\",",
        "  \"formType\": \"default\",",
        "  \"formKey\": \"purchase-form\",",
        "  \"nodes\": [",
        "    {",
        "      \"type\": \"发起节点\",",
        "      \"name\": \"发起节点\",",
        "      \"id\": \"start-a\",",
        "      \"permission\": [],",
        "      \"next\": \"fork-a\"",
        "    },",
        "    {",
        "      \"type\": \"分流节点\",",
        "      \"name\": \"金额判断\",",
        "      \"id\": \"fork-a\",",
        "      \"logic\": \"exclusive\",",
        "      \"branches\": [",
        "        {",
        "          \"id\": \"branch-1\",",
        "          \"name\": \"金额大于1000\",",
        "          \"condition\": {",
        "            \"left\": {",
        "              \"type\": \"field\",",
        "              \"field\": \"amount\"",
        "            },",
        "            \"op\": \"greater\",",
        "            \"right\": 1000",
        "          },",
        "          \"first\": \"approve-a\"",
        "        },",
        "        {",
        "          \"id\": \"branch-2\",",
        "          \"name\": \"默认\",",
        "          \"condition\": {",
        "            \"left\": {",
        "              \"type\": \"field\",",
        "              \"field\": \"amount\"",
        "            },",
        "            \"op\": \"less_or_equal\",",
        "            \"right\": 1000",
        "          },",
        "          \"first\": \"approve-b\"",
        "        }",
        "      ],",
        "      \"next\": \"end-a\"",
        "    },",
        "    {",
        "      \"type\": \"审批节点\",",
        "      \"name\": \"经理审批\",",
        "      \"id\": \"approve-a\",",
        "      \"subType\": \"单签\",",
        "      \"candidates\": [],",
        "      \"permission\": [],",
        "      \"actions\": [\"reject\"],",
        "      \"next\": \"\"",
        "    },",
        "    {",
        "      \"type\": \"审批节点\",",
        "      \"name\": \"直属审批\",",
        "      \"id\": \"approve-b\",",
        "      \"subType\": \"单签\",",
        "      \"candidates\": [],",
        "      \"permission\": [],",
        "      \"actions\": [\"reject\"],",
        "      \"next\": \"\"",
        "    },",
        "    {",
        "      \"type\": \"结束节点\",",
        "      \"name\": \"结束\",",
        "      \"id\": \"end-a\",",
        "      \"end\": {",
        "        \"content\": \"done\",",
        "        \"channel\": [\"message\"],",
        "        \"candidates\": []",
        "      },",
        "      \"next\": \"\"",
        "    }",
        "  ]",
        "}");
  }

  private String v2FlowJson() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"category\": \"\",",
        "  \"icon\": \"shenpi\",",
        "  \"description\": \"\",",
        "  \"formType\": \"default\",",
        "  \"formKey\": \"purchase-form\",",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"start-a\", \"permission\": [] },",
        "    { \"type\": \"审批节点\", \"name\": \"经理审批\", \"id\": \"approve-a\", \"subType\": \"单签\", \"candidates\": [], \"permission\": [], \"actions\": [\"reject\"] },",
        "    { \"type\": \"审批节点\", \"name\": \"直属审批\", \"id\": \"approve-b\", \"subType\": \"单签\", \"candidates\": [], \"permission\": [], \"actions\": [\"reject\"] },",
        "    { \"type\": \"结束节点\", \"name\": \"结束\", \"id\": \"end-a\", \"end\": { \"content\": \"done\", \"channel\": [\"message\"], \"candidates\": [] } }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-a\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"greater\", \"right\": 1000 }, \"order\": 1 },",
        "    { \"id\": \"e2\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"approve-b\", \"condition\": { \"left\": { \"type\": \"field\", \"field\": \"amount\" }, \"op\": \"less_or_equal\", \"right\": 1000 }, \"order\": 2 },",
        "    { \"id\": \"e3\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-a\", \"targetId\": \"end-a\" },",
        "    { \"id\": \"e4\", \"type\": \"SEQUENCE\", \"sourceId\": \"approve-b\", \"targetId\": \"end-a\" }",
        "  ]",
        "}");
  }

  private String v2FlowJsonWithId(String id) {
    return v2FlowJson().replace("\"id\": \"flow-a\"", "\"id\": \"" + id + "\"");
  }

  private void assertCanonicalStaticMetadata(JsonNode root) {
    assertEquals(1, root.path("formatVersion").asInt());
    assertEquals("flow-a", root.path("name").asText());
    assertEquals("采购审批", root.path("title").asText());
    assertEquals("default", root.path("form").path("formType").asText());
    assertEquals("purchase-form", root.path("form").path("formId").asText());
    assertEquals(120, root.path("editorLayout").path("nodePositions").path("start-a").path("x").asInt());
    assertTrue(root.path("edges").isArray());
    assertEquals("StartNode", root.path("nodes").get(0).path("type").asText());
    assertEquals("EndNode", root.path("nodes").get(1).path("type").asText());
    assertFalse(root.has("id"));
    assertFalse(root.has("formType"));
    assertFalse(root.has("formKey"));
  }

  private String v2FlowJsonWithEditorMeta() {
    return String.join("\n",
        "{",
        "  \"formatVersion\": 2,",
        "  \"id\": \"flow-a\",",
        "  \"name\": \"采购审批\",",
        "  \"category\": \"\",",
        "  \"icon\": \"shenpi\",",
        "  \"description\": \"\",",
        "  \"formType\": \"default\",",
        "  \"formKey\": \"purchase-form\",",
        "  \"editorLayout\": {",
        "    \"nodePositions\": {",
        "      \"start-a\": { \"x\": 120, \"y\": 80 }",
        "    }",
        "  },",
        "  \"nodes\": [",
        "    { \"type\": \"发起节点\", \"name\": \"发起节点\", \"id\": \"start-a\", \"permission\": [] },",
        "    { \"type\": \"结束节点\", \"name\": \"结束\", \"id\": \"end-a\", \"end\": { \"content\": \"done\", \"channel\": [\"message\"], \"candidates\": [] } }",
        "  ],",
        "  \"edges\": [",
        "    { \"id\": \"e1\", \"type\": \"SEQUENCE\", \"sourceId\": \"start-a\", \"targetId\": \"end-a\" }",
        "  ]",
        "}");
  }
}
