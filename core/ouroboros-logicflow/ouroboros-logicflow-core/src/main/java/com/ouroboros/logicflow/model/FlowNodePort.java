package com.ouroboros.logicflow.model;

public interface FlowNodePort {
  Integer getIndex();

  String getName();

  String getDescription();

  DataTypeEnum getDataType();

  default String getExclusiveGroupId() {
    return null;
  }

  default Boolean isDefaultBranch() {
    return false;
  }

  FlowNode getFlowNode();
}
