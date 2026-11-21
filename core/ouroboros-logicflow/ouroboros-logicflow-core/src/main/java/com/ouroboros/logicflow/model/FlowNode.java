package com.ouroboros.logicflow.model;

import java.beans.Transient;
import java.util.List;

import io.vavr.control.Try;

import com.ouroboros.logicflow.exception.NodeDesignException;

public interface FlowNode {
  String getName();

  String getDescription();

  String getType();

  List<FlowNodePort> getInputPorts();

  List<FlowNodePort> getOutputPorts();

  Boolean isAsync();

  Boolean isEnabled();

  Boolean isValid();

  @Transient
  Try<List<Object>> execute(List<Object> inputs);

  @Transient
  List<FlowConnection> getIncomingConnections();

  @Transient
  List<FlowConnection> getOutgoingConnections();

  @Transient
  List<NodeDesignException> getErrors();

  @Transient
  default Boolean supportsSelectiveOutputMetadata() {
    return false;
  }
}
