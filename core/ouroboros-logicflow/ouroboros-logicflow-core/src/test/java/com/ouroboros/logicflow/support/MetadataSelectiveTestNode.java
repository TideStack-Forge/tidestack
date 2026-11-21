package com.ouroboros.logicflow.support;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractNode;
import com.ouroboros.logicflow.results.Decision;

public class MetadataSelectiveTestNode extends AbstractNode<Decision> {
  public MetadataSelectiveTestNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
  }

  @Override
  protected List<Decision> doExecute(List<Object> inputs) {
    final var output = new ArrayList<Decision>(getOutputPorts().size());
    IntStream.range(0, getOutputPorts().size()).forEach(i -> output.add(Decision.unsatisfied()));
    output.set(0, Decision.satisfied(inputs.isEmpty() ? null : inputs.get(0)));
    return output;
  }

  @Override
  protected Integer maxOutputPortCount() {
    return Integer.MAX_VALUE;
  }

  @Override
  public Boolean supportsSelectiveOutputMetadata() {
    return true;
  }
}
