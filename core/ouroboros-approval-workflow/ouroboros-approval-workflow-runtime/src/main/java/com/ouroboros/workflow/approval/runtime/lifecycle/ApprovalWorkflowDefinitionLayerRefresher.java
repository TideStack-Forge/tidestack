package com.ouroboros.workflow.approval.runtime.lifecycle;

import java.util.List;
import java.util.Objects;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import com.ouroboros.AppContext;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;
import com.ouroboros.metadata.resource.MetadataSource;
import com.ouroboros.workflow.approval.definition.metadata.ApprovalWorkflowDefinitionMetadata;
import com.ouroboros.workflow.approval.manage.service.WorkflowModelService;

public class ApprovalWorkflowDefinitionLayerRefresher {
  private final ApprovalWorkflowMetadataDefinitionProvider definitionProvider;
  private final WorkflowModelService workflowModelService;

  private List<ApprovalWorkflowDefinitionMetadata> pendingDefinitions = List.of();
  private boolean snapshotReady;
  private RuntimeException snapshotFailure;

  public ApprovalWorkflowDefinitionLayerRefresher(
      AppContext appContext,
      WorkflowModelService workflowModelService) {
    this(new ApprovalWorkflowMetadataDefinitionProvider(appContext), workflowModelService);
  }

  ApprovalWorkflowDefinitionLayerRefresher(
      AppContext appContext,
      WorkflowModelService workflowModelService,
      MetadataSource jarMetadataSource) {
    this(new ApprovalWorkflowMetadataDefinitionProvider(appContext, jarMetadataSource), workflowModelService);
  }

  public ApprovalWorkflowDefinitionLayerRefresher(
      ApprovalWorkflowMetadataDefinitionProvider definitionProvider,
      WorkflowModelService workflowModelService) {
    this.definitionProvider = Objects.requireNonNull(definitionProvider);
    this.workflowModelService = Objects.requireNonNull(workflowModelService);
  }

  @EventListener
  public synchronized void onMetadataRefreshing(OuroborosMetadataRefreshingEvent event) {
    prepareSnapshot();
  }

  @EventListener
  @Order(100)
  public synchronized void onModelRefreshed(OuroborosModelRefreshedEvent event) throws OuroborosException {
    if (snapshotFailure != null) {
      throw snapshotFailure;
    }
    if (!snapshotReady) {
      prepareSnapshot();
    }
    try {
      workflowModelService.refreshDefinitionLayers(pendingDefinitions);
      pendingDefinitions = List.of();
      snapshotReady = false;
    } catch (RuntimeException e) {
      snapshotFailure = e;
      throw e;
    }
  }

  private void prepareSnapshot() {
    snapshotReady = false;
    snapshotFailure = null;
    try {
      pendingDefinitions = List.copyOf(definitionProvider.discoverWorkflowDefinitions());
      snapshotReady = true;
    } catch (RuntimeException e) {
      pendingDefinitions = List.of();
      snapshotFailure = e;
      throw e;
    }
  }
}
