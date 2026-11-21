package com.ouroboros.logicflow.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.results.Decision;
import com.ouroboros.logicflow.results.NamedOutPortValueSetter;
import com.ouroboros.logicflow.results.OutPortValueSetter;

public abstract class AbstractDecisionNode extends AbstractNode<Decision> {

  protected AbstractDecisionNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  @Override
  protected List<Decision> doExecute(List<Object> params) {
    final var portsResult = new ArrayList<Decision>(getOutputPorts().size());
    IntStream.range(0, getOutputPorts().size()).forEach(i -> portsResult.add(Decision.unsatisfied()));
    OutPortValueSetter setPortResult = (portIndex, value) -> {
      if (portIndex >= getOutputPorts().size()) {
        throw new FlowRuntimeException("Out port index out of range: " + portIndex, this, params);
      }
      portsResult.set(portIndex, Decision.satisfied(value));
    };
    NamedOutPortValueSetter setNamedPortResult = (portName, value) -> {
      var outPort = getOutputPorts().stream().filter(p -> p.getName().equalsIgnoreCase(portName)).findFirst();
      outPort.map(getOutputPorts()::indexOf).ifPresent(index -> portsResult.set(index, Decision.satisfied(value)));
      if (!outPort.isPresent()) {
        throw new FlowRuntimeException("Out port not found: " + portName, this, params);
      }
    };
    makeDecision(params, setPortResult, setNamedPortResult);
    return portsResult;
  }

  protected abstract void makeDecision(List<Object> inputs, OutPortValueSetter setPort, NamedOutPortValueSetter setNamedPort);

  @Override
  public Boolean supportsSelectiveOutputMetadata() {
    return true;
  }
}
