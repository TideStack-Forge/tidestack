package com.ouroboros.logicflow.model;

public interface FlowConnection {
  String getLabel();

  String getDescription();

  String getSourceName();

  Integer getSourceOutputPort();

  String getTargetName();

  Integer getTargetInputPort();

  FlowNode getSourceNode();

  FlowNode getTargetNode();
}
