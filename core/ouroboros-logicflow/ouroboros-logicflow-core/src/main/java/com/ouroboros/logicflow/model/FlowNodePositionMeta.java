package com.ouroboros.logicflow.model;

import com.ouroboros.logicflow.model.support.AbstractLogicFlowModel;

public class FlowNodePositionMeta extends AbstractLogicFlowModel<FlowNodePositionMeta, FlowNodePositionMeta.Builder> {
  private Double x;
  private Double y;

  private FlowNodePositionMeta() {
    super();
  }

  public static Builder builder() {
    return new Builder();
  }

  public Double getX() {
    return x;
  }

  public Double getY() {
    return y;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractLogicFlowModel.Builder<FlowNodePositionMeta> {
    private Builder() {
      super();
      model = new FlowNodePositionMeta();
    }

    public Builder x(Double x) {
      model.x = x;
      return this;
    }

    public Builder y(Double y) {
      model.y = y;
      return this;
    }
  }
}
