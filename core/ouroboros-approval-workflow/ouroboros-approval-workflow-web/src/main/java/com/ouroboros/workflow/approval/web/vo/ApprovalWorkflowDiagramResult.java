package com.ouroboros.workflow.approval.web.vo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public class ApprovalWorkflowDiagramResult {
  private JsonNode definition;
  private JsonNode nodes;
  private JsonNode edges;
  private List<String> activeNodeIds;

  public JsonNode getDefinition() {
    return definition;
  }

  public void setDefinition(JsonNode definition) {
    this.definition = definition;
  }

  public JsonNode getNodes() {
    return nodes;
  }

  public void setNodes(JsonNode nodes) {
    this.nodes = nodes;
  }

  public JsonNode getEdges() {
    return edges;
  }

  public void setEdges(JsonNode edges) {
    this.edges = edges;
  }

  public List<String> getActiveNodeIds() {
    return activeNodeIds;
  }

  public void setActiveNodeIds(List<String> activeNodeIds) {
    this.activeNodeIds = activeNodeIds;
  }
}
