package com.ouroboros.logicflow.model;

import com.ouroboros.logicflow.model.support.AbstractLogicFlowModel;

public class FlowConnectionMeta extends AbstractLogicFlowModel<FlowConnectionMeta, FlowConnectionMeta.Builder> {
  private String label;
  private String description;
  private String sourceNode;
  private Integer sourceOutputPort;
  private String targetNode;
  private Integer targetInputPort;

  private FlowConnectionMeta() {
    super();
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getLabel() {
    return label;
  }

  public String getDescription() {
    return description;
  }

  public String getSourceNode() {
    return sourceNode;
  }

  public Integer getSourceOutputPort() {
    return sourceOutputPort;
  }

  public String getTargetNode() {
    return targetNode;
  }

  public Integer getTargetInputPort() {
    return targetInputPort;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractLogicFlowModel.Builder<FlowConnectionMeta> {
    private Builder() {
      super();
      this.model = new FlowConnectionMeta();
    }

    public Builder label(String label) {
      model.label = label;
      return this;
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder sourceNode(String sourceNode) {
      model.sourceNode = sourceNode;
      return this;
    }

    public Builder sourceOutputPort(Integer sourceOutputPort) {
      model.sourceOutputPort = sourceOutputPort;
      return this;
    }

    public Builder targetNode(String targetNode) {
      model.targetNode = targetNode;
      return this;
    }

    public Builder targetInputPort(Integer targetInputPort) {
      model.targetInputPort = targetInputPort;
      return this;
    }
  }
}
