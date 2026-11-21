package com.ouroboros.logicflow.model;

import java.beans.Transient;
import java.util.List;

import io.vavr.control.Try;

import com.ouroboros.logicflow.exception.DesignException;

public interface FlowModel {
  String getName();

  /** Stable digest of the published flow definition used for execution auditing. */
  default String getRevision() {
    return getName();
  }

  String getDescription();

  List<FlowNode> getNodes();

  List<FlowConnection> getConnections();

  Boolean isEnabled();

  Boolean isValid();

  DataTypeEnum getInputDataType();

  DataTypeEnum getOutputDataType();

  @Transient
  Try<Object> execute(Object... inputs);

  @Transient
  FlowNode getEntryNode();

  @Transient
  FlowNode getExitNode();

  @Transient
  default FlowNode getErrorEntryNode() {
    return null;
  }

  @Transient
  FlowNode getNode(String name);

  @Transient
  List<DesignException> getErrors();
}
