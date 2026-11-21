package com.ouroboros.logicflow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ouroboros.logicflow.model.support.AbstractLogicFlowModel;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowModelValidationDiagnostic extends AbstractLogicFlowModel<FlowModelValidationDiagnostic, FlowModelValidationDiagnostic.Builder> {
  private String diagnosticKind;
  private String message;
  private List<String> nodeNames = new ArrayList<>();
  private String scope;
  private String fieldPath;
  private String portKind;
  private Integer portIndex;
  private String sourceNode;
  private Integer sourceOutputPort;
  private String targetNode;
  private Integer targetInputPort;

  private FlowModelValidationDiagnostic() {
    super();
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getDiagnosticKind() {
    return diagnosticKind;
  }

  public String getMessage() {
    return message;
  }

  public List<String> getNodeNames() {
    return Collections.unmodifiableList(nodeNames);
  }

  public String getScope() {
    return scope;
  }

  public String getFieldPath() {
    return fieldPath;
  }

  public String getPortKind() {
    return portKind;
  }

  public Integer getPortIndex() {
    return portIndex;
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
  public FlowModelValidationDiagnostic clone() {
    var cloned = super.clone();
    cloned.nodeNames = new ArrayList<>(nodeNames);
    return cloned;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractLogicFlowModel.Builder<FlowModelValidationDiagnostic> {
    private Builder() {
      super();
      this.model = new FlowModelValidationDiagnostic();
    }

    public Builder diagnosticKind(String diagnosticKind) {
      model.diagnosticKind = diagnosticKind;
      return this;
    }

    public Builder message(String message) {
      model.message = message;
      return this;
    }

    public Builder nodeNames(List<String> nodeNames) {
      model.nodeNames = nodeNames == null ? new ArrayList<>() : new ArrayList<>(nodeNames);
      return this;
    }

    public Builder scope(String scope) {
      model.scope = scope;
      return this;
    }

    public Builder fieldPath(String fieldPath) {
      model.fieldPath = fieldPath;
      return this;
    }

    public Builder portKind(String portKind) {
      model.portKind = portKind;
      return this;
    }

    public Builder portIndex(Integer portIndex) {
      model.portIndex = portIndex;
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
