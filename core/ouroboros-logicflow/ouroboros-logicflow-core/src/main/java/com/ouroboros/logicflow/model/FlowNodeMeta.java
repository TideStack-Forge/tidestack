package com.ouroboros.logicflow.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.logicflow.model.support.AbstractLogicFlowExtraPropsModel;

public class FlowNodeMeta extends AbstractLogicFlowExtraPropsModel<FlowNodeMeta, FlowNodeMeta.Builder> {
  private String name;
  private String description;
  private String type;
  private List<FlowNodePortMeta> inputPorts = new ArrayList<>();
  private List<FlowNodePortMeta> outputPorts = new ArrayList<>();
  @JsonProperty("isAsync")
  private Boolean isAsync = false;
  @JsonProperty("isEnabled")
  private Boolean isEnabled = true;

  private FlowNodeMeta() {
    super();
  }

  static public Builder builder() {
    return new Builder();
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getType() {
    return type;
  }

  public List<FlowNodePortMeta> getInputPorts() {
    return inputPorts;
  }

  public List<FlowNodePortMeta> getOutputPorts() {
    return outputPorts;
  }

  @JsonProperty("isAsync")
  public Boolean isAsync() {
    return isAsync;
  }

  @JsonProperty("isEnabled")
  public Boolean isEnabled() {
    return isEnabled;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractLogicFlowExtraPropsModel.Builder<FlowNodeMeta, Builder> {
    private Builder() {
      super();
      model = new FlowNodeMeta();
    }

    public Builder name(String name) {
      model.name = name;
      return this;
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder type(String type) {
      model.type = type;
      return this;
    }

    public Builder inputPorts(List<FlowNodePortMeta> inputPorts) {
      model.inputPorts = inputPorts;
      return this;
    }

    public Builder outputPorts(List<FlowNodePortMeta> outputPorts) {
      model.outputPorts = outputPorts;
      return this;
    }

    public Builder inputPort(FlowNodePortMeta inputPort) {
      model.inputPorts.add(inputPort);
      return this;
    }

    public Builder inputPort(String name) {
      model.inputPorts.add(FlowNodePortMeta.builder().name(name).build());
      return this;
    }

    public Builder inputPort(String name, String dataType) {
      model.inputPorts.add(FlowNodePortMeta.builder().name(name).dataType(dataType).build());
      return this;
    }

    public Builder inputPort(String name, String dataType, String description) {
      model.inputPorts.add(FlowNodePortMeta.builder().name(name).dataType(dataType).description(description).build());
      return this;
    }

    public Builder outputPort(FlowNodePortMeta outputPort) {
      model.outputPorts.add(outputPort);
      return this;
    }

    public Builder async() {
      model.isAsync = true;
      return this;
    }

    public Builder async(Boolean isAsync) {
      model.isAsync = isAsync;
      return this;
    }

    public Builder enabled(Boolean isEnabled) {
      model.isEnabled = isEnabled;
      return this;
    }
  }
}
