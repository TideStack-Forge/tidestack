package com.ouroboros.workflow.approval.web.service;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.workflow.approval.web.vo.ApprovalWorkflowDiagramResult;

public class ApprovalWorkflowDiagramResultAssembler {

  public ApprovalWorkflowDiagramResult assemble(JsonNode definition, List<String> activeNodeIds) {
    ApprovalWorkflowDiagramResult result = new ApprovalWorkflowDiagramResult();
    result.setDefinition(definition);
    result.setNodes(definition == null ? null : definition.path("nodes"));
    result.setEdges(definition == null ? null : definition.path("edges"));
    result.setActiveNodeIds(activeNodeIds);
    return result;
  }
}
