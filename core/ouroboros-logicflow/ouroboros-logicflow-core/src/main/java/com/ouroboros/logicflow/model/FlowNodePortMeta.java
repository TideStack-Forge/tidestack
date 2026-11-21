package com.ouroboros.logicflow.model;

import java.beans.Transient;

import com.ouroboros.logicflow.model.support.AbstractLogicFlowModel;

public class FlowNodePortMeta extends AbstractLogicFlowModel<FlowNodePortMeta, FlowNodePortMeta.Builder> {
  private String name;
  private String description;
  private String dataType = "any";
  private String exclusiveGroupId;
  private Boolean defaultBranch = false;

  private FlowNodePortMeta() {
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

  public String getDataType() {
    return dataType;
  }

  public String getExclusiveGroupId() {
    return exclusiveGroupId;
  }

  public Boolean isDefaultBranch() {
    return defaultBranch;
  }

  @Transient
  public DataTypeEnum getDataTypeEnum() {
    return DataTypeEnum.fromValue(dataType);
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractLogicFlowModel.Builder<FlowNodePortMeta> {
    private Builder() {
      super();
      this.model = new FlowNodePortMeta();
    }

    public Builder name(String name) {
      model.name = name;
      return this;
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder dataType(String dataType) {
      model.dataType = dataType;
      return this;
    }

    public Builder dataType(DataTypeEnum dataType) {
      model.dataType = dataType.getValue();
      return this;
    }

    public Builder exclusiveGroupId(String exclusiveGroupId) {
      model.exclusiveGroupId = exclusiveGroupId;
      return this;
    }

    public Builder defaultBranch(Boolean defaultBranch) {
      model.defaultBranch = defaultBranch;
      return this;
    }
  }
}
