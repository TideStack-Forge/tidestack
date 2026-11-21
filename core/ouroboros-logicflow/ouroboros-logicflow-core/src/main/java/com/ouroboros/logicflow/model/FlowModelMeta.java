package com.ouroboros.logicflow.model;

import java.beans.Transient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.logicflow.model.support.AbstractLogicFlowModel;

//TODO: 需要增补 title 等默认元数据
public class FlowModelMeta extends AbstractLogicFlowModel<FlowModelMeta, FlowModelMeta.Builder> {
  private final static Logger logger = LoggerFactory.getLogger(FlowModelMeta.class);
  private String name;
  private String description;
  private String entryNode;
  private String exitNode;
  private String errorEntryNode;
  private String inputDataType = "any";
  private String outputDataType = "any";
  /** Optional authoring contracts. They describe data; they do not execute or validate an invocation. */
  private Map<String, Object> inputSchema = Map.of();
  private Map<String, Object> outputSchema = Map.of();
  private List<FlowNodeMeta> nodes = new ArrayList<>();
  private List<FlowConnectionMeta> connections = new ArrayList<>();
  private Map<String, FlowNodePositionMeta> nodePositions = new LinkedHashMap<>();
  @JsonProperty("isEnabled")
  private Boolean isEnabled = true;
  private String validationSummary;
  private List<String> validationErrors = new ArrayList<>();
  private List<FlowModelValidationDiagnostic> validationDiagnostics = new ArrayList<>();
  private LocalDateTime lastValidatedTime;

  private FlowModelMeta() {
    super();
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getEntryNode() {
    return entryNode;
  }

  public String getExitNode() {
    return exitNode;
  }

  public String getErrorEntryNode() {
    return errorEntryNode;
  }

  public Map<String, Object> getInputSchema() { return Collections.unmodifiableMap(inputSchema); }

  public Map<String, Object> getOutputSchema() { return Collections.unmodifiableMap(outputSchema); }

  public String getInputDataType() {
    return inputDataType;
  }

  public String getOutputDataType() {
    return outputDataType;
  }

  @Transient
  public DataTypeEnum getInputDataTypeEnum() {
    return DataTypeEnum.fromValue(inputDataType);
  }

  @Transient
  public DataTypeEnum getOutputDataTypeEnum() {
    return DataTypeEnum.fromValue(outputDataType);
  }

  public List<FlowNodeMeta> getNodes() {
    return Collections.unmodifiableList(nodes);
  }

  public List<FlowConnectionMeta> getConnections() {
    return Collections.unmodifiableList(connections);
  }

  public Map<String, FlowNodePositionMeta> getNodePositions() {
    return Collections.unmodifiableMap(nodePositions);
  }

  public FlowModelValidationState getValidationState() {
    if (!validationDiagnostics.isEmpty() || !validationErrors.isEmpty()) {
      return FlowModelValidationState.INVALID;
    }
    if (validationSummary != null && !validationSummary.trim().isEmpty()) {
      return FlowModelValidationState.INVALID;
    }
    if (lastValidatedTime != null) {
      return FlowModelValidationState.VALID;
    }
    return null;
  }

  public String getValidationSummary() {
    return validationSummary;
  }

  public List<String> getValidationErrors() {
    return Collections.unmodifiableList(validationErrors);
  }

  public List<FlowModelValidationDiagnostic> getValidationDiagnostics() {
    return Collections.unmodifiableList(validationDiagnostics);
  }

  public LocalDateTime getLastValidatedTime() {
    return lastValidatedTime;
  }

  @JsonProperty("isEnabled")
  public Boolean isEnabled() {
    return isEnabled;
  }

  @Transient
  @Override
  protected Builder createBuilder() {
    return builder();
  }

  @Override
  public FlowModelMeta clone() {
    var cloned = super.clone();
    cloned.inputSchema = new LinkedHashMap<>(inputSchema);
    cloned.outputSchema = new LinkedHashMap<>(outputSchema);
    cloned.nodes = nodes.stream().map(FlowNodeMeta::clone).collect(Collectors.toList());
    cloned.connections = connections.stream().map(FlowConnectionMeta::clone).collect(Collectors.toList());
    cloned.nodePositions = nodePositions.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> entry.getValue() == null ? null : entry.getValue().clone(),
            (left, right) -> right,
            LinkedHashMap::new));
    cloned.validationErrors = new ArrayList<>(validationErrors);
    cloned.validationDiagnostics = validationDiagnostics.stream()
        .map(FlowModelValidationDiagnostic::clone)
        .collect(Collectors.toList());
    cloned.lastValidatedTime = lastValidatedTime;
    return cloned;
  }

  public static class Builder extends AbstractLogicFlowModel.Builder<FlowModelMeta> {
    private Builder() {
      super();
      this.model = new FlowModelMeta();
    }

    public Builder name(String name) {
      model.name = name;
      return this;
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder entryNode(FlowNodeMeta entryNode) {
      node(entryNode);
      model.entryNode = entryNode.getName();
      return this;
    }

    public Builder entryNode(String entryNode) {
      model.entryNode = entryNode;
      return this;
    }

    public Builder exitNode(FlowNodeMeta exitNode) {
      connect(exitNode);
      model.exitNode = exitNode.getName();
      return this;
    }

    public Builder exitNode(String exitNode) {
      model.exitNode = exitNode;
      return this;
    }

    public Builder errorEntryNode(FlowNodeMeta errorEntryNode) {
      node(errorEntryNode);
      model.errorEntryNode = errorEntryNode.getName();
      return this;
    }

    public Builder errorEntryNode(String errorEntryNode) {
      model.errorEntryNode = errorEntryNode;
      return this;
    }

    public Builder nodes(List<FlowNodeMeta> nodes) {
      model.nodes = nodes;
      return this;
    }

    public Builder connections(List<FlowConnectionMeta> connections) {
      model.connections = connections;
      return this;
    }

    public Builder nodePositions(Map<String, FlowNodePositionMeta> nodePositions) {
      model.nodePositions = nodePositions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nodePositions);
      return this;
    }

    public Builder nodePosition(String nodeName, FlowNodePositionMeta nodePosition) {
      model.nodePositions.put(nodeName, nodePosition);
      return this;
    }

    public Builder node(FlowNodeMeta node) {
      model.nodes.add(node);
      return this;
    }

    public Builder connection(FlowConnectionMeta connection) {
      model.connections.add(connection);
      return this;
    }

    public Builder connect(FlowNodeMeta nextNode, int prevNodeOutputPort, int nextNodeInputPort) {
      node(nextNode);
      try {
        model.getNodes().get(model.getNodes().size() - 2).getName();
      } catch (Exception e) {
        logger.error("无法获取上一节点名称");
      }
      if (model.getNodes().isEmpty()) {
        return this;
      }
      var connection = FlowConnectionMeta.builder()
          .sourceNode(model.getNodes().get(model.getNodes().size() - 2).getName())
          .sourceOutputPort(prevNodeOutputPort)
          .targetNode(nextNode.getName())
          .targetInputPort(nextNodeInputPort)
          .build();
      connection(connection);
      return this;
    }

    public Builder connect(FlowNodeMeta nextNode) {
      return connect(nextNode, 0, 0);
    }

    public Builder connect(String sourceName, String targetName) {
      return connect(sourceName, 0, targetName, 0);
    }

    public Builder connect(String sourceName, String targetName, int targetPort) {
      return connect(sourceName, 0, targetName, targetPort);
    }

    public Builder connect(String sourceNode, int sourcePort, String targetNode, int targetPort) {
      var connection = FlowConnectionMeta.builder()
          .sourceNode(sourceNode)
          .sourceOutputPort(sourcePort)
          .targetNode(targetNode)
          .targetInputPort(targetPort)
          .build();
      connection(connection);
      return this;
    }

    public Builder enabled(Boolean isEnabled) {
      model.isEnabled = isEnabled;
      return this;
    }

    public Builder validationSummary(String validationSummary) {
      model.validationSummary = validationSummary;
      return this;
    }

    public Builder validationErrors(List<String> validationErrors) {
      model.validationErrors = validationErrors == null ? new ArrayList<>() : new ArrayList<>(validationErrors);
      return this;
    }

    public Builder validationDiagnostics(List<FlowModelValidationDiagnostic> validationDiagnostics) {
      model.validationDiagnostics = validationDiagnostics == null
          ? new ArrayList<>()
          : validationDiagnostics.stream().map(FlowModelValidationDiagnostic::clone).collect(Collectors.toList());
      return this;
    }

    public Builder lastValidatedTime(LocalDateTime lastValidatedTime) {
      model.lastValidatedTime = lastValidatedTime;
      return this;
    }

    public Builder inputSchema(Map<String, Object> schema) {
      model.inputSchema = new LinkedHashMap<>(schema == null ? Map.of() : schema);
      return this;
    }

    public Builder outputSchema(Map<String, Object> schema) {
      model.outputSchema = new LinkedHashMap<>(schema == null ? Map.of() : schema);
      return this;
    }

    public Builder inputDataType(String inputDataType) {
      model.inputDataType = inputDataType;
      return this;
    }

    public Builder outputDataType(String outputDataType) {
      model.outputDataType = outputDataType;
      return this;
    }

  }
}
