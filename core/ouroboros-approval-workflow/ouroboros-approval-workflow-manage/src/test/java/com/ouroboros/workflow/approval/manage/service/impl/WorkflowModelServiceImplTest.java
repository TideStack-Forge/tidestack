package com.ouroboros.workflow.approval.manage.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vavr.control.Try;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.InternalException;
import com.ouroboros.util.Json;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.workflow.approval.FlowManageService;
import com.ouroboros.workflow.approval.FlowDeploymentResult;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionDocument;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.manage.entity.WorkflowModel;

public class WorkflowModelServiceImplTest {
  @Test
  void customDeploymentRechecksTheCurrentFormBeforeCallingTheEngine() {
    var service = new TestWorkflowModelService();
    service.flowManageService = mock(FlowManageService.class);
    service.workflowModel = new WorkflowModel("flow-a", "采购审批", "draft", "", "",
        workflowMetadata("flow-a", "采购审批", 1, true).replace("\"default\"", "\"custom\""), "tester", null, "", "");
    var provider = mock(com.ouroboros.workflow.approval.FlowFormProvider.class);
    when(provider.getType()).thenReturn("custom");
    var form = new com.ouroboros.workflow.approval.model.FlowForm();
    form.setName("metadata-form");
    form.setFields(List.of());
    form.setExpressionVariables(List.of());
    when(provider.get("metadata-form")).thenReturn(Optional.of(form));
    service.flowFormProviders = List.of(provider);
    when(service.flowManageService.deployForRuntime(anyString(), anyString()))
        .thenReturn(Try.success(FlowDeploymentResult.builtIn("deployment-1")));
    service.deploy("flow-a");
    assertEquals("deployed", service.lastPartialUpdate.get("status"));
    when(provider.get("metadata-form")).thenReturn(Optional.empty());
    service.workflowModel.setStatus("updated");
    assertThrows(BusinessException.class, () -> service.deploy("flow-a"));
    verify(service.flowManageService, org.mockito.Mockito.times(1)).deployForRuntime(anyString(), anyString());
  }

  @Test
  void searchByPageShouldSearchByNameOrIdWhenKeywordPresent() throws Exception {
    WorkflowModelDataModel dataModel = mock(WorkflowModelDataModel.class);
    when(dataModel.count(any())).thenReturn(Try.success(1L));
    when(dataModel.query(any(), any(), anyString(), any(), any())).thenReturn(Try.success(Collections.emptyList()));

    SearchWorkflowModelService service = new SearchWorkflowModelService(dataModel);

    PagedWebApiResult<WorkflowModel> result = service.searchByPage("purchase", "draft", 2, 10);

    assertEquals(Long.valueOf(1L), result.getData().getTotal());

    verify(dataModel).count(argThat(where -> {
      assertWorkflowSearchWhere(where);
      return true;
    }));
    verify(dataModel).query(any(), any(), org.mockito.ArgumentMatchers.eq("saveAt desc"), org.mockito.ArgumentMatchers.eq(10), org.mockito.ArgumentMatchers.eq(10));
  }

  private void assertWorkflowSearchWhere(Map<String, Object> where) {
    assertTrue(where.containsKey("or"));
    assertTrue(where.containsKey("status"));

    List<?> orList = assertInstanceOf(List.class, where.get("or"));
    assertEquals(2, orList.size());
    Map<?, ?> nameCondition = assertInstanceOf(Map.class, orList.get(0));
    Map<?, ?> idCondition = assertInstanceOf(Map.class, orList.get(1));
    assertEquals("%purchase%", assertInstanceOf(Map.class, nameCondition.get("name")).get("like"));
    assertEquals("%purchase%", assertInstanceOf(Map.class, idCondition.get("id")).get("like"));
    assertEquals("draft", assertInstanceOf(Map.class, where.get("status")).get("="));
  }

  @Test
  void deployConvertsStoredStaticMetadataBeforeCallingFlowManageService() throws Exception {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = new WorkflowModel(
        "flow-a",
        "采购审批",
        "draft",
        "shenpi",
        "",
        workflowMetadataWithEditorLayout("flow-a", "采购审批", 7, true),
        "tester",
        null,
        "",
        ""
    );

    when(flowManageService.deployForRuntime(anyString(), anyString()))
        .thenReturn(Try.success(FlowDeploymentResult.builtIn("process-1")));

    service.deploy("flow-a");

    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    verify(flowManageService).deployForRuntime(org.mockito.ArgumentMatchers.eq("flow-a"), jsonCaptor.capture());

    JsonNode normalized = Json.toBean(JsonNode.class, jsonCaptor.getValue());
    assertEquals(2, normalized.path("formatVersion").asInt());
    assertEquals("flow-a", normalized.path("id").asText());
    assertEquals("采购审批", normalized.path("name").asText());
    assertEquals(120, normalized.path("editorLayout").path("nodePositions").path("start").path("x").asInt());
    assertEquals(420, normalized.path("editorLayout").path("nodePositions").path("end").path("x").asInt());
    assertTrue(normalized.path("edges").isArray());
    assertNotNull(service.lastPartialUpdate);
    assertEquals("deployed", service.lastPartialUpdate.get("status"));
    assertEquals("process-1", service.lastPartialUpdate.get("processDefinitionId"));
  }

  @Test
  void deployRejectsDisabledDefinitionBeforeCallingFlowManageService() {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = new WorkflowModel(
        "flow-a",
        "采购审批",
        "draft",
        "shenpi",
        "",
        workflowMetadata("flow-a", "采购审批", 7, false),
        "tester",
        null,
        "",
        ""
    );

    assertThrows(BusinessException.class, () -> service.deploy("flow-a"));

    verifyNoInteractions(flowManageService);
  }

  @Test
  void deploySuccessForMetadataLayerModelDoesNotMarkDefinitionAdopted() throws Exception {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 2);
    service.workflowModel.setStatus("updated");
    service.workflowModel.setProcessDefinitionId("process-old");

    when(flowManageService.deployForRuntime(anyString(), anyString()))
        .thenReturn(Try.success(FlowDeploymentResult.builtIn("process-new")));

    service.deploy("flow-a");

    assertNotNull(service.lastPartialUpdate);
    assertEquals("deployed", service.lastPartialUpdate.get("status"));
    assertEquals("process-new", service.lastPartialUpdate.get("processDefinitionId"));
    assertFalse(service.lastPartialUpdate.containsKey("adopted"));
  }

  @Test
  void deployPersistsRuntimeDeploymentIdentityReturnedByRouter() throws Exception {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = new WorkflowModel(
        "flow-a", "采购审批", "draft", "shenpi", "",
        workflowMetadata("flow-a", "采购审批", 8, true), "tester", null, "", "");

    when(flowManageService.deployForRuntime(anyString(), anyString())).thenReturn(Try.success(
        new FlowDeploymentResult("EXTERNAL", "FULL_PROCESS", "EXTERNAL_ENGINE", "dingtalk",
            "mapping-1", 3, null, "corp-a:definition-1")));

    service.deploy("flow-a");

    assertEquals("deployed", service.lastPartialUpdate.get("status"));
    assertNull(service.lastPartialUpdate.get("processDefinitionId"));
    assertEquals("EXTERNAL", service.lastPartialUpdate.get("executionMode"));
    assertEquals("FULL_PROCESS", service.lastPartialUpdate.get("integrationMode"));
    assertEquals("dingtalk", service.lastPartialUpdate.get("engineAdapterId"));
    assertEquals("mapping-1", service.lastPartialUpdate.get("engineMappingId"));
    assertEquals(3, service.lastPartialUpdate.get("engineMappingVersion"));
    assertEquals("corp-a:definition-1", service.lastPartialUpdate.get("externalDefinitionRef"));
  }

  @Test
  void deployCanonicalizesNativeV2StableFieldsAndKeepsEditorLayout() throws Exception {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = new WorkflowModel(
        "flow-a",
        "采购审批",
        "draft",
        "shenpi",
        "",
        v2FlowJsonWithEditorMeta(),
        "tester",
        null,
        "",
        ""
    );

    when(flowManageService.deployForRuntime(anyString(), anyString()))
        .thenReturn(Try.success(FlowDeploymentResult.builtIn("process-1")));

    service.deploy("flow-a");

    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    verify(flowManageService).deployForRuntime(org.mockito.ArgumentMatchers.eq("flow-a"), jsonCaptor.capture());

    JsonNode normalized = Json.toBean(JsonNode.class, jsonCaptor.getValue());
    assertEquals(120, normalized.path("editorLayout").path("nodePositions").path("start-a").path("x").asInt());
    assertEquals("StartNode", normalized.path("nodes").get(0).path("type").asText());
    assertEquals("EndNode", normalized.path("nodes").get(1).path("type").asText());
  }

  @Test
  void deployRejectsInvalidStoredJsonBeforeCallingFlowManageService() {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = new WorkflowModel(
        "flow-a",
        "采购审批",
        "draft",
        "shenpi",
        "",
        "{\"formatVersion\":2,\"id\":\"flow-a\",\"nodes\":[",
        "tester",
        null,
        "",
        ""
    );

    assertThrows(InternalException.class, () -> service.deploy("flow-a"));
    verifyNoInteractions(flowManageService);
  }

  @Test
  void deployFailureKeepsRuntimeModelStateUntouched() {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = new WorkflowModel(
        "flow-a",
        "采购审批",
        "updated",
        "shenpi",
        "",
        workflowMetadata("flow-a", "采购审批", 8, true),
        "tester",
        null,
        "process-1",
        ""
    );

    when(flowManageService.deployForRuntime(anyString(), anyString()))
        .thenReturn(Try.failure(new InternalException("Flowable 部署失败")));

    assertThrows(InternalException.class, () -> service.deploy("flow-a"));
    assertNull(service.lastPartialUpdate);
    assertEquals("updated", service.workflowModel.getStatus());
    assertEquals("process-1", service.workflowModel.getProcessDefinitionId());
  }

  @Test
  void refreshMissingDefinitionInsertsDraftWithoutDeploying() throws Exception {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;

    boolean changed = service.refreshDefinitionLayer(metadataLayerWorkflowModel("flow-a", 1));

    assertTrue(changed);
    assertNotNull(service.insertedLayerModel);
    assertEquals("flow-a", service.insertedLayerModel.getId());
    assertEquals("draft", service.insertedLayerModel.getStatus());
    assertEquals(WorkflowModel.SOURCE_METADATA_LAYER, service.insertedLayerModel.getSource());
    assertEquals(Integer.valueOf(1), service.insertedLayerModel.getVersion());
    assertFalse(service.insertedLayerModel.getAdopted());
    verifyNoInteractions(flowManageService);
  }

  @Test
  void refreshDefinitionLayerUpdatesUnadoptedModelWhenIncomingVersionIsNewer() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 1);

    boolean changed = service.refreshDefinitionLayer(metadataLayerWorkflowModel("flow-a", 2));

    assertTrue(changed);
    assertNotNull(service.updatedLayerModel);
    assertEquals("flow-a", service.updatedLayerModel.getId());
    assertEquals("draft", service.updatedLayerModel.getStatus());
    assertEquals("", service.updatedLayerModel.getProcessDefinitionId());
    assertEquals(Integer.valueOf(2), service.updatedLayerModel.getVersion());
    assertFalse(service.updatedLayerModel.getAdopted());
  }

  @Test
  void refreshDefinitionLayerMarksDeployedModelUpdatedWhenIncomingVersionIsNewer() throws Exception {
    FlowManageService flowManageService = mock(FlowManageService.class);
    when(flowManageService.offline(anyString())).thenReturn(Try.success(null));
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 1);
    service.workflowModel.setStatus("deployed");
    service.workflowModel.setProcessDefinitionId("process-1");

    boolean changed = service.refreshDefinitionLayer(metadataLayerWorkflowModel("flow-a", 2));

    assertTrue(changed);
    assertNotNull(service.updatedLayerModel);
    assertEquals("updated", service.updatedLayerModel.getStatus());
    assertEquals("", service.updatedLayerModel.getProcessDefinitionId());
    assertEquals(Integer.valueOf(2), service.updatedLayerModel.getVersion());
    // 清空指针前必须挂起旧部署，否则旧部署指针已失、引擎仍 active
    verify(flowManageService).offline("process-1");
  }

  @Test
  void refreshDefinitionLayerMarksOfflineModelUpdatedWhenIncomingVersionIsNewer() throws Exception {
    FlowManageService flowManageService = mock(FlowManageService.class);
    when(flowManageService.offline(anyString())).thenReturn(Try.success(null));
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 1);
    service.workflowModel.setStatus("offline");
    service.workflowModel.setProcessDefinitionId("process-1");

    boolean changed = service.refreshDefinitionLayer(metadataLayerWorkflowModel("flow-a", 2));

    assertTrue(changed);
    assertNotNull(service.updatedLayerModel);
    assertEquals("updated", service.updatedLayerModel.getStatus());
    assertEquals("", service.updatedLayerModel.getProcessDefinitionId());
    assertEquals(Integer.valueOf(2), service.updatedLayerModel.getVersion());
  }

  @Test
  void refreshDefinitionLayerSkipsSameVersion() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 2);

    boolean changed = service.refreshDefinitionLayer(metadataLayerWorkflowModel("flow-a", 2));

    assertFalse(changed);
    assertNull(service.updatedLayerModel);
  }

  @Test
  void refreshDefinitionLayerSkipsOlderVersion() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 2);

    boolean changed = service.refreshDefinitionLayer(metadataLayerWorkflowModel("flow-a", 1));

    assertFalse(changed);
    assertNull(service.updatedLayerModel);
  }

  @Test
  void refreshDefinitionLayerSkipsAdoptedMetadataLayerModel() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 1);
    service.workflowModel.setAdopted(true);

    boolean changed = service.refreshDefinitionLayer(metadataLayerWorkflowModel("flow-a", 2));

    assertFalse(changed);
    assertNull(service.updatedLayerModel);
  }

  @Test
  void refreshDefinitionLayerFailsWhenInstanceRecordUsesSameId() {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 1);
    service.workflowModel.setSource(WorkflowModel.SOURCE_INSTANCE);

    assertThrows(BusinessException.class, () -> service.refreshDefinitionLayer(metadataLayerWorkflowModel("flow-a", 2)));
    assertNull(service.updatedLayerModel);
  }

  @Test
  void refreshDefinitionLayersRefreshesSnapshotWithoutDeploying() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = mock(FlowManageService.class);

    int changed = service.refreshDefinitionLayers(List.of(metadata("resource-definition", 3)));

    assertEquals(1, changed);
    assertNotNull(service.insertedLayerModel);
    assertEquals("resource-definition", service.insertedLayerModel.getId());
    assertEquals(Integer.valueOf(3), service.insertedLayerModel.getVersion());
    verifyNoInteractions(service.flowManageService);
  }

  @Test
  void refreshDefinitionLayerFailsWhenSameVersionContentChanges() {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 2);
    service.workflowModel.setStatus("deployed");
    service.workflowModel.setProcessDefinitionId("process-1");
    WorkflowModel incoming = metadataLayerWorkflowModel("flow-a", 2);
    incoming.setDescription("Source metadata corrected without version bump");

    assertThrows(BusinessException.class, () -> service.refreshDefinitionLayer(incoming));

    assertNull(service.updatedLayerModel);
  }

  @Test
  void refreshDefinitionLayersFailsWhenSnapshotContainsDuplicateDefinitionIds() {
    TestWorkflowModelService service = new TestWorkflowModelService();

    assertThrows(BusinessException.class, () -> service.refreshDefinitionLayers(List.of(
        metadata("flow-a", 1),
        metadata("flow-a", 1)
    )));
    assertNull(service.insertedLayerModel);
    assertNull(service.updatedLayerModel);
  }

  @Test
  void refreshDefinitionLayersOfflinesMissingUnadoptedMetadataLayerModel() throws Exception {
    FlowManageService flowManageService = mock(FlowManageService.class);
    when(flowManageService.offline(anyString())).thenReturn(Try.success(null));
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    WorkflowModel existing = metadataLayerWorkflowModel("removed-flow", 1);
    existing.setStatus("deployed");
    existing.setProcessDefinitionId("process-1");
    service.queriedLayerModels = List.of(existing);

    int changed = service.refreshDefinitionLayers(List.of());

    assertEquals(1, changed);
    assertNotNull(service.lastPartialUpdate);
    assertEquals("offline", service.lastPartialUpdate.get("status"));
    assertFalse(service.lastPartialUpdate.containsKey("processDefinitionId"));
    // 只改 DB 状态不阻止新发起：必须按既有下线机制挂起引擎部署
    verify(flowManageService).offline("process-1");
  }

  @Test
  void partialUpdateMovesDeployedDefinitionBackToUpdatedWhenContentChanges() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    WorkflowModel existing = metadataLayerWorkflowModel("flow-a", 1);
    existing.setStatus("deployed");
    existing.setAdopted(true);
    service.workflowModel = existing;

    Map<String, Object> update = new HashMap<>();
    update.put("json", workflowMetadata("flow-a", "采购审批", 2, true));
    service.partialUpdate("flow-a", update);

    assertNotNull(service.lastPartialUpdate);
    assertEquals("updated", service.lastPartialUpdate.get("status"));
  }

  @Test
  void offlineSuspendsEngineDefinitionBeforeMarkingRuntimeRecordOffline() throws Exception {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 1);
    service.workflowModel.setStatus(WorkflowModel.STATUS_DEPLOYED);
    service.workflowModel.setProcessDefinitionId("flow-a:1:100");

    when(flowManageService.offline("flow-a:1:100")).thenReturn(Try.success(null));

    service.offline("flow-a");

    verify(flowManageService).offline("flow-a:1:100");
    assertNotNull(service.lastPartialUpdate);
    assertEquals("offline", service.lastPartialUpdate.get("status"));
  }

  @Test
  void offlineFailureKeepsRuntimeRecordStateUntouched() {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 1);
    service.workflowModel.setStatus(WorkflowModel.STATUS_DEPLOYED);
    service.workflowModel.setProcessDefinitionId("flow-a:1:100");

    when(flowManageService.offline("flow-a:1:100")).thenReturn(Try.failure(new IllegalStateException("engine down")));

    assertThrows(InternalException.class, () -> service.offline("flow-a"));

    assertNull(service.lastPartialUpdate);
  }

  @Test
  void refreshDefinitionLayersKeepsMissingAdoptedMetadataLayerModel() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    WorkflowModel existing = metadataLayerWorkflowModel("removed-flow", 1);
    existing.setStatus("deployed");
    existing.setProcessDefinitionId("process-1");
    existing.setAdopted(true);
    service.queriedLayerModels = List.of(existing);

    int changed = service.refreshDefinitionLayers(List.of());

    assertEquals(0, changed);
    assertNull(service.lastPartialUpdate);
  }

  @Test
  void resetOfflinesEngineDefinitionThenRestoresMetadataLayerDraft() throws Exception {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 9);
    service.workflowModel.setName("实例定制采购审批");
    service.workflowModel.setStatus(WorkflowModel.STATUS_DEPLOYED);
    service.workflowModel.setProcessDefinitionId("flow-a:9:900");
    service.workflowModel.setAdopted(true);
    service.designTimeDefinitionProviders = List.of(id -> Optional.of(metadata(id, 3)));

    when(flowManageService.offline("flow-a:9:900")).thenReturn(Try.success(null));

    service.reset("flow-a");

    verify(flowManageService).offline("flow-a:9:900");
    assertNotNull(service.updatedLayerModel);
    assertEquals("flow-a", service.updatedLayerModel.getId());
    assertEquals("采购审批", service.updatedLayerModel.getName());
    assertEquals(WorkflowModel.STATUS_DRAFT, service.updatedLayerModel.getStatus());
    assertEquals("", service.updatedLayerModel.getProcessDefinitionId());
    assertEquals(WorkflowModel.SOURCE_METADATA_LAYER, service.updatedLayerModel.getSource());
    assertEquals(Integer.valueOf(3), service.updatedLayerModel.getVersion());
    assertFalse(service.updatedLayerModel.getAdopted());
  }

  @Test
  void resetFailureToOfflineEngineDefinitionKeepsRuntimeRecordUntouched() {
    FlowManageService flowManageService = mock(FlowManageService.class);
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.flowManageService = flowManageService;
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 9);
    service.workflowModel.setStatus(WorkflowModel.STATUS_DEPLOYED);
    service.workflowModel.setProcessDefinitionId("flow-a:9:900");
    service.workflowModel.setAdopted(true);
    service.designTimeDefinitionProviders = List.of(id -> Optional.of(metadata(id, 3)));

    when(flowManageService.offline("flow-a:9:900")).thenReturn(Try.failure(new IllegalStateException("engine down")));

    assertThrows(InternalException.class, () -> service.reset("flow-a"));

    assertNull(service.updatedLayerModel);
  }

  @Test
  void resetRejectsInstanceDefinition() {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 1);
    service.workflowModel.setSource(WorkflowModel.SOURCE_INSTANCE);
    service.designTimeDefinitionProviders = List.of(id -> Optional.of(metadata(id, 1)));

    assertThrows(BusinessException.class, () -> service.reset("flow-a"));

    assertNull(service.updatedLayerModel);
  }

  @Test
  void resetFailsWhenSourceDefinitionIsMissing() {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 1);
    service.workflowModel.setAdopted(true);
    service.designTimeDefinitionProviders = List.of(id -> Optional.empty());

    assertThrows(BusinessException.class, () -> service.reset("flow-a"));

    assertNull(service.updatedLayerModel);
  }

  @Test
  void updateMetadataLayerRuntimeRecordMarksAdoptedAndKeepsCanonicalDefinitionVersion() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 7);

    WorkflowModel runtimeUpdate = metadataLayerWorkflowModel("flow-a", 9);
    runtimeUpdate.setName("自定义采购审批");
    runtimeUpdate.setSource(WorkflowModel.SOURCE_INSTANCE);
    runtimeUpdate.setAdopted(false);
    service.update("flow-a", runtimeUpdate);

    assertNotNull(service.runtimeUpdatedModel);
    assertEquals(WorkflowModel.SOURCE_METADATA_LAYER, service.runtimeUpdatedModel.getSource());
    assertEquals(Integer.valueOf(9), service.runtimeUpdatedModel.getVersion());
    assertTrue(service.runtimeUpdatedModel.getAdopted());
  }

  @Test
  void updateMetadataLayerRuntimeRecordWithoutDefinitionChangesKeepsOwnershipAndDoesNotAdopt() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 7);

    WorkflowModel runtimeUpdate = metadataLayerWorkflowModel("flow-a", 7);
    runtimeUpdate.setStatus("deployed");
    runtimeUpdate.setSource(WorkflowModel.SOURCE_INSTANCE);
    runtimeUpdate.setAdopted(false);
    service.update("flow-a", runtimeUpdate);

    assertNotNull(service.runtimeUpdatedModel);
    assertEquals(WorkflowModel.SOURCE_METADATA_LAYER, service.runtimeUpdatedModel.getSource());
    assertEquals(Integer.valueOf(7), service.runtimeUpdatedModel.getVersion());
    assertFalse(service.runtimeUpdatedModel.getAdopted());
  }

  @Test
  void addInstanceRuntimeRecordUsesCanonicalDefinitionVersion() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    WorkflowModel runtimeModel = new WorkflowModel(
        "flow-a",
        "采购审批",
        "draft",
        "shenpi",
        "",
        v2FlowJson(),
        "tester",
        null,
        "",
        ""
    );
    runtimeModel.setVersion(null);
    runtimeModel.setAdopted(null);

    service.add(runtimeModel);

    assertNotNull(service.insertedRuntimeModel);
    assertEquals(WorkflowModel.SOURCE_INSTANCE, service.insertedRuntimeModel.getSource());
    assertEquals(Integer.valueOf(1), service.insertedRuntimeModel.getVersion());
    assertFalse(service.insertedRuntimeModel.getAdopted());
  }

  @Test
  void partialUpdateMetadataLayerDefinitionFieldsMarksAdopted() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 1);
    java.util.Map<String, Object> update = new java.util.HashMap<>();
    update.put("json", v2FlowJson());

    service.partialUpdate("flow-a", update);

    assertNotNull(service.lastPartialUpdate);
    assertEquals(true, service.lastPartialUpdate.get("adopted"));
    assertEquals(Integer.valueOf(1), service.lastPartialUpdate.get("version"));
  }

  @Test
  void statusOnlyPartialUpdateMetadataLayerRecordDoesNotMarkAdopted() throws Exception {
    TestWorkflowModelService service = new TestWorkflowModelService();
    service.workflowModel = metadataLayerWorkflowModel("flow-a", 1);
    java.util.Map<String, Object> update = new java.util.HashMap<>();
    update.put("status", "deployed");

    service.partialUpdate("flow-a", update);

    assertNotNull(service.lastPartialUpdate);
    assertFalse(service.lastPartialUpdate.containsKey("adopted"));
  }

  static class TestWorkflowModelService extends WorkflowModelServiceImpl {
    WorkflowModel workflowModel;
    Map<String, Object> lastPartialUpdate;
    WorkflowModel insertedRuntimeModel;
    WorkflowModel insertedLayerModel;
    WorkflowModel updatedLayerModel;
    WorkflowModel runtimeUpdatedModel;
    List<WorkflowModel> queriedLayerModels = List.of();

    @Override
    public WorkflowModel getByPrimaryKey(String primaryKey) {
      return workflowModel;
    }

    @Override
    protected WorkflowModel insertLayerModel(WorkflowModel definition) {
      insertedLayerModel = definition;
      return definition;
    }

    @Override
    protected WorkflowModel insertRuntimeModel(WorkflowModel model) {
      insertedRuntimeModel = model;
      return model;
    }

    @Override
    protected Boolean updateLayerModel(String id, WorkflowModel definition) {
      updatedLayerModel = definition;
      return true;
    }

    @Override
    protected Boolean updateRuntimeModel(String id, WorkflowModel model) {
      runtimeUpdatedModel = model;
      return true;
    }

    @Override
    protected Boolean partialUpdateRuntimeModel(String id, Map<String, Object> map) {
      lastPartialUpdate = map;
      return true;
    }

    @Override
    protected List<WorkflowModel> queryLayerModels() {
      return queriedLayerModels;
    }
  }

  static class SearchWorkflowModelService extends WorkflowModelServiceImpl {
    private final TypedDataModel<String, WorkflowModel> dataModel;

    SearchWorkflowModelService(TypedDataModel<String, WorkflowModel> dataModel) {
      this.dataModel = dataModel;
    }

    @Override
    protected TypedDataModel<String, WorkflowModel> getDataModel() {
      return dataModel;
    }
  }

  interface WorkflowModelDataModel extends TypedDataModel<String, WorkflowModel> {
  }

  private String v2FlowJson() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-a",
          "name": "采购审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "purchase-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "审批节点", "name": "经理审批", "id": "approve-a", "subType": "单签", "candidates": [], "permission": [], "actions": ["reject"] },
            { "type": "审批节点", "name": "直属审批", "id": "approve-b", "subType": "单签", "candidates": [], "permission": [], "actions": ["reject"] },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-a", "condition": { "left": { "type": "field", "field": "amount" }, "op": "greater", "right": 1000 }, "order": 1 },
            { "id": "e2", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "approve-b", "condition": { "left": { "type": "field", "field": "amount" }, "op": "less_or_equal", "right": 1000 }, "order": 2 },
            { "id": "e3", "type": "SEQUENCE", "sourceId": "approve-a", "targetId": "end-a" },
            { "id": "e4", "type": "SEQUENCE", "sourceId": "approve-b", "targetId": "end-a" }
          ]
        }
        """;
  }

  private String workflowMetadata(String name, String title, int version, boolean enabled) {
    return String.format("""
        {
          "formatVersion": 1,
          "name": "%s",
          "title": "%s",
          "description": "Loaded from app metadata",
          "version": %d,
          "enabled": %s,
          "form": {
            "formType": "default",
            "formId": "metadata-form"
          },
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start", "permission": [] },
            { "type": "结束节点", "name": "结束", "id": "end" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start", "targetId": "end" }
          ]
        }
        """, name, title, version, enabled);
  }

  private String workflowMetadataWithEditorLayout(String name, String title, int version, boolean enabled) {
    return String.format("""
        {
          "formatVersion": 1,
          "name": "%s",
          "title": "%s",
          "description": "Loaded from app metadata",
          "version": %d,
          "enabled": %s,
          "form": {
            "formType": "default",
            "formId": "metadata-form"
          },
          "editorLayout": {
            "nodePositions": {
              "start": { "x": 120, "y": 80 },
              "end": { "x": 420, "y": 80 }
            }
          },
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start", "permission": [] },
            { "type": "结束节点", "name": "结束", "id": "end" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start", "targetId": "end" }
          ]
        }
        """, name, title, version, enabled);
  }

  private String v1FlowJson() {
    return """
        {
          "id": "flow-a",
          "name": "采购审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "purchase-form",
          "nodes": [
            {
              "type": "发起节点",
              "name": "发起节点",
              "id": "start-a",
              "permission": [],
              "next": "fork-a"
            },
            {
              "type": "分流节点",
              "name": "金额判断",
              "id": "fork-a",
              "logic": "exclusive",
              "branches": [
                {
                  "id": "branch-1",
                  "name": "金额大于1000",
                  "condition": {
                    "left": {
                      "type": "field",
                      "field": "amount"
                    },
                    "op": "greater",
                    "right": 1000
                  },
                  "first": "approve-a"
                },
                {
                  "id": "branch-2",
                  "name": "默认",
                  "condition": {
                    "left": {
                      "type": "field",
                      "field": "amount"
                    },
                    "op": "less_or_equal",
                    "right": 1000
                  },
                  "first": "approve-b"
                }
              ],
              "next": "end-a"
            },
            {
              "type": "审批节点",
              "name": "经理审批",
              "id": "approve-a",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject"],
              "next": ""
            },
            {
              "type": "审批节点",
              "name": "直属审批",
              "id": "approve-b",
              "subType": "单签",
              "candidates": [],
              "permission": [],
              "actions": ["reject"],
              "next": ""
            },
            {
              "type": "结束节点",
              "name": "结束",
              "id": "end-a",
              "next": "",
              "next": ""
            }
          ]
        }
        """;
  }

  private String v2FlowJsonWithEditorMeta() {
    return """
        {
          "formatVersion": 2,
          "id": "flow-a",
          "name": "采购审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "purchase-form",
          "editorLayout": {
            "nodePositions": {
              "start-a": { "x": 120, "y": 80 }
            }
          },
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "end-a" }
          ]
        }
        """;
  }

  private WorkflowModel metadataLayerWorkflowModel(String id, Integer version) {
    WorkflowModel workflowModel = new WorkflowModel(
        id,
        "采购审批",
        "draft",
        "shenpi",
        "",
        workflowMetadata(id, "采购审批", version, true),
        "metadata-layer",
        LocalDateTime.now(),
        "",
        ""
    );
    workflowModel.setSource(WorkflowModel.SOURCE_METADATA_LAYER);
    workflowModel.setVersion(version);
    workflowModel.setAdopted(false);
    return workflowModel;
  }

  private ApprovalWorkflowDefinitionMetadata metadata(String id, int version) {
    return ApprovalWorkflowDefinitionDocument.decode(workflowMetadata(id, "采购审批", version, true))
        .get()
        .metadata();
  }

  private String v2FlowJson(String id) {
    return String.format("""
        {
          "formatVersion": 2,
          "id": "%s",
          "name": "采购审批",
          "category": "",
          "icon": "shenpi",
          "description": "",
          "formType": "default",
          "formKey": "purchase-form",
          "nodes": [
            { "type": "发起节点", "name": "发起节点", "id": "start-a", "permission": [] },
            { "type": "结束节点", "name": "结束", "id": "end-a" }
          ],
          "edges": [
            { "id": "e1", "type": "SEQUENCE", "sourceId": "start-a", "targetId": "end-a" }
          ]
        }
        """, id);
  }
}
