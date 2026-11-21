package com.ouroboros.workflow.approval.runtime.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import com.ouroboros.AppContext;
import com.ouroboros.exception.InternalException;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;

class ApprovalWorkflowDefinitionLayerRefresherTest {
  @TempDir
  Path appRoot;

  @Test
  void metadataRefreshingBuildsSnapshotFromAppRootWithoutRefreshing() throws Exception {
    WorkflowModelService service = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionLayerRefresher refresher = refresher(service);
    writeWorkflow("expenseApproval", workflowMetadata("expenseApproval", "费用审批", 3, true));

    refresher.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this));

    verify(service, never()).refreshDefinitionLayers(anyList());
  }

  @Test
  void modelRefreshedRefreshesSnapshotAfterMetadataRefreshing() throws Exception {
    WorkflowModelService service = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionLayerRefresher refresher = refresher(service);
    writeWorkflow("expenseApproval", workflowMetadata("expenseApproval", "费用审批", 3, true));

    refresher.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this));
    refresher.onModelRefreshed(new OuroborosModelRefreshedEvent(this));

    ArgumentCaptor<List<ApprovalWorkflowDefinitionMetadata>> captor = ArgumentCaptor.forClass(List.class);
    verify(service).refreshDefinitionLayers(captor.capture());
    List<ApprovalWorkflowDefinitionMetadata> definitions = captor.getValue();
    assertEquals(1, definitions.size());
    ApprovalWorkflowDefinitionMetadata definition = definitions.get(0);
    assertEquals("expenseApproval", definition.getName());
    assertEquals("费用审批", definition.getTitle());
    assertEquals(Integer.valueOf(3), definition.getVersion());
    assertEquals("businessModel", definition.getForm().getFormType());
    assertEquals("expense-request", definition.getForm().getFormId());
    assertEquals(2, definition.getNodes().size());
    assertEquals(1, definition.getEdges().size());
  }

  @Test
  void missingDirectoryProducesEmptySnapshot() throws Exception {
    WorkflowModelService service = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionLayerRefresher refresher = refresher(service);

    refresher.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this));
    refresher.onModelRefreshed(new OuroborosModelRefreshedEvent(this));

    ArgumentCaptor<List<ApprovalWorkflowDefinitionMetadata>> captor = ArgumentCaptor.forClass(List.class);
    verify(service).refreshDefinitionLayers(captor.capture());
    assertEquals(0, captor.getValue().size());
  }

  @Test
  void invalidWorkflowAssetFailsMetadataRefreshing() throws Exception {
    WorkflowModelService service = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionLayerRefresher refresher = refresher(service);
    writeWorkflow("expenseApproval", "{\"formatVersion\":1}");

    assertThrows(InternalException.class, () -> refresher.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this)));
    verify(service, never()).refreshDefinitionLayers(anyList());
  }

  @Test
  void fileNameAndMetadataNameMustMatch() throws Exception {
    WorkflowModelService service = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionLayerRefresher refresher = refresher(service);
    writeWorkflow("expenseApproval", workflowMetadata("otherApproval", "费用审批", 1, true));

    assertThrows(InternalException.class, () -> refresher.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this)));
  }

  @Test
  void duplicateDefinitionIdsFailMetadataRefreshingBeforeRefreshing() throws Exception {
    WorkflowModelService service = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionLayerRefresher refresher = refresher(service);
    writeWorkflow("expenseApproval", workflowMetadata("expenseApproval", "费用审批", 1, true));
    writeWorkflow("nested/expenseApproval", workflowMetadata("expenseApproval", "嵌套费用审批", 1, true));

    assertThrows(InternalException.class, () -> refresher.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this)));
    verify(service, never()).refreshDefinitionLayers(anyList());
  }

  @Test
  void failedMetadataRefreshingDoesNotRefreshStaleSnapshotOnModelRefreshed() throws Exception {
    WorkflowModelService service = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionLayerRefresher refresher = refresher(service);
    writeWorkflow("expenseApproval", workflowMetadata("expenseApproval", "费用审批", 1, true));
    refresher.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this));
    refresher.onModelRefreshed(new OuroborosModelRefreshedEvent(this));
    clearInvocations(service);

    writeWorkflow("expenseApproval", "{\"formatVersion\":1}");
    assertThrows(InternalException.class, () -> refresher.onMetadataRefreshing(new OuroborosMetadataRefreshingEvent(this)));
    Files.delete(appRoot.resolve("workflow/expenseApproval.workflow.json"));

    assertThrows(InternalException.class, () -> refresher.onModelRefreshed(new OuroborosModelRefreshedEvent(this)));
    verify(service, never()).refreshDefinitionLayers(anyList());
  }

  @Test
  void modelRefreshedCanDiscoverWhenMetadataPhaseWasNotCalled() throws Exception {
    WorkflowModelService service = mock(WorkflowModelService.class);
    ApprovalWorkflowDefinitionLayerRefresher refresher = refresher(service);
    writeWorkflow("expenseApproval", workflowMetadata("expenseApproval", "费用审批", 1, false));

    refresher.onModelRefreshed(new OuroborosModelRefreshedEvent(this));

    ArgumentCaptor<List<ApprovalWorkflowDefinitionMetadata>> captor = ArgumentCaptor.forClass(List.class);
    verify(service).refreshDefinitionLayers(captor.capture());
    assertNotNull(captor.getValue());
    assertEquals("expenseApproval", captor.getValue().get(0).getName());
  }

  @Test
  void metadataDefinitionProviderCanProvideSourceDefinitionForReset() throws Exception {
    ApprovalWorkflowMetadataDefinitionProvider provider = new ApprovalWorkflowMetadataDefinitionProvider(appContext(appRoot));
    writeWorkflow("expenseApproval", workflowMetadata("expenseApproval", "费用审批", 2, true));

    var definition = provider.findDefinition("expenseApproval");

    assertEquals(true, definition.isPresent());
    assertEquals("expenseApproval", definition.get().getName());
    assertEquals("费用审批", definition.get().getTitle());
    assertEquals(Integer.valueOf(2), definition.get().getVersion());
  }

  private ApprovalWorkflowDefinitionLayerRefresher refresher(WorkflowModelService service) {
    return new ApprovalWorkflowDefinitionLayerRefresher(appContext(appRoot), service);
  }

  private AppContext appContext(Path root) {
    return () -> root.toString();
  }

  private void writeWorkflow(String name, String content) throws Exception {
    Path workflowDir = appRoot.resolve("workflow");
    Files.createDirectories(workflowDir);
    Path file = workflowDir.resolve(name + ".workflow.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
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
            "formType": "businessModel",
            "formId": "expense-request"
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
}
