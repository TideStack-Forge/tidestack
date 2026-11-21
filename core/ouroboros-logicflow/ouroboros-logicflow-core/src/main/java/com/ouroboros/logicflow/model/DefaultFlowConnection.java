package com.ouroboros.logicflow.model;

public class DefaultFlowConnection implements FlowConnection {
  final FlowConnectionMeta meta;
  final FlowModel flowModel;
  FlowNode sourceNode;
  FlowNode targetNode;

  public DefaultFlowConnection(FlowModel flowModel, FlowConnectionMeta meta) {
    this.flowModel = flowModel;
    this.meta = meta;
  }

  @Override
  public String getLabel() {
    return meta.getLabel();
  }

  @Override
  public String getDescription() {
    return meta.getDescription();
  }

  @Override
  public String getSourceName() {
    return meta.getSourceNode();
  }

  @Override
  public Integer getSourceOutputPort() {
    return meta.getSourceOutputPort();
  }

  @Override
  public String getTargetName() {
    return meta.getTargetNode();
  }

  @Override
  public Integer getTargetInputPort() {
    return meta.getTargetInputPort();
  }

  @Override
  public FlowNode getSourceNode() {
    return sourceNode;
  }

  void setSourceNode(FlowNode sourceNode) {
    this.sourceNode = sourceNode;
  }

  @Override
  public FlowNode getTargetNode() {
    return targetNode;
  }

  void setTargetNode(FlowNode targetNode) {
    this.targetNode = targetNode;
  }
}
