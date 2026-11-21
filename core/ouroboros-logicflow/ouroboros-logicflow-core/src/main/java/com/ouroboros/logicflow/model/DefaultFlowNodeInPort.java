package com.ouroboros.logicflow.model;

public class DefaultFlowNodeInPort implements FlowNodePort {
  private final Integer index;
  private final FlowNodePortMeta portMeta;
  private final DataTypeEnum dataType;
  private final FlowNode node;

  public DefaultFlowNodeInPort(FlowNode node, Integer index, FlowNodePortMeta portMeta) {
    this.index = index;
    this.portMeta = portMeta;
    this.dataType = portMeta.getDataTypeEnum();
    this.node = node;
  }

  public DefaultFlowNodeInPort(FlowNode node, Integer index, DataTypeEnum dataType) {
    this(node, index, FlowNodePortMeta.builder().name("input" + (index + 1)).dataType(dataType.getValue()).build());
  }

  @Override
  public Integer getIndex() {
    return index;
  }

  @Override
  public FlowNode getFlowNode() {
    return node;
  }

  @Override
  public String getName() {
    return portMeta.getName();
  }

  @Override
  public String getDescription() {
    return portMeta.getDescription();
  }

  @Override
  public DataTypeEnum getDataType() {
    return dataType;
  }

  @Override
  public String getExclusiveGroupId() {
    return portMeta.getExclusiveGroupId();
  }

  @Override
  public Boolean isDefaultBranch() {
    return portMeta.isDefaultBranch();
  }
}
