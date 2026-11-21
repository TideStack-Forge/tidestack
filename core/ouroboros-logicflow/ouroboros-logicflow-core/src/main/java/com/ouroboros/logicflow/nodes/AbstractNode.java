package com.ouroboros.logicflow.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;

import io.vavr.control.Try;

import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.*;

public abstract class AbstractNode<T> implements FlowNode {
  protected final FlowModel flowModel;
  protected final FlowNodeMeta meta;
  protected final List<NodeDesignException> errors = new ArrayList<>();
  private final List<FlowConnection> incomingConnections;
  private final List<FlowConnection> outgoingConnections;

  protected AbstractNode(FlowModel flowModel, FlowNodeMeta meta) {
    this.flowModel = flowModel;
    this.meta = meta;
    var connections = flowModel.getConnections();
    incomingConnections = connections.stream()
        .filter(c -> Objects.equals(c.getTargetName(), getName()))
        .collect(Collectors.toList());
    outgoingConnections = connections.stream()
        .filter(c -> Objects.equals(c.getSourceName(), getName()))
        .collect(Collectors.toList());
  }

  @Override
  public String getName() {
    return meta.getName();
  }

  @Override
  public String getDescription() {
    return meta.getDescription();
  }

  @Override
  public String getType() {
    return meta.getType();
  }

  @Override
  public List<FlowNodePort> getInputPorts() {
    var inputPortFromMeta = meta.getInputPorts();
    var maxPortIndex = getIncomingConnections().stream()
        .map(FlowConnection::getTargetInputPort)
        .max(Integer::compareTo)
        .orElse(-1);
    var finalInputPort = Math.min(Math.max(Math.max(inputPortFromMeta.size(), maxPortIndex + 1), minInputPortCount()), maxInputPortCount());
    return IntStream.range(0, finalInputPort)
        .mapToObj(i -> (inputPortFromMeta.size() > i)
            ? new DefaultFlowNodeInPort(this, i, inputPortFromMeta.get(i))
            : new DefaultFlowNodeInPort(this, i, DataTypeEnum.ANY))
        .collect(Collectors.toList());
  }

  @Override
  public List<FlowNodePort> getOutputPorts() {
    var outPortFromMeta = meta.getOutputPorts();
    var maxPortIndex = getOutgoingConnections().stream()
        .map(FlowConnection::getSourceOutputPort)
        .max(Integer::compareTo)
        .orElse(-1);
    var finalOutPortCount = Math.min(Math.max(Math.max(outPortFromMeta.size(), maxPortIndex + 1), minOutputPortCount()), maxOutputPortCount());
    return IntStream.range(0, finalOutPortCount)
        .mapToObj(i -> (outPortFromMeta.size() > i)
            ? new DefaultFlowNodeOutPort(this, i, outPortFromMeta.get(i))
            : new DefaultFlowNodeOutPort(this, i, DataTypeEnum.ANY))
        .collect(Collectors.toList());
  }

  protected Integer maxInputPortCount() {
    return Integer.MAX_VALUE;
  }

  protected Integer minInputPortCount() {
    return 1;
  }

  protected Integer maxOutputPortCount() {
    return 1;
  }

  protected Integer minOutputPortCount() {
    return 1;
  }

  @Override
  public List<FlowConnection> getIncomingConnections() {
    return incomingConnections;
  }

  @Override
  public List<FlowConnection> getOutgoingConnections() {
    return outgoingConnections;
  }

  @Override
  public Boolean isAsync() {
    return meta.isAsync();
  }

  @Override
  public Boolean isEnabled() {
    return meta.isEnabled();
  }

  @Override
  public Boolean isValid() {
    return errors.isEmpty();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public final Try<List<Object>> execute(List<Object> inputs) {
    return Try.of(() -> (List) doExecute(inputs));
  }

  protected abstract List<T> doExecute(List<Object> inputs);

  protected void addError(NodeDesignException e) {
    errors.add(e);
  }

  @Override
  public List<NodeDesignException> getErrors() {
    return Collections.unmodifiableList(errors);
  }

  public void validateOutputPortMetadata() {
    getOutputPorts().stream()
        .filter(port -> Boolean.TRUE.equals(port.isDefaultBranch()))
        .filter(port -> StringUtils.isBlank(port.getExclusiveGroupId()))
        .findFirst()
        .ifPresent(port -> addError(new NodeDesignException(
            "defaultBranch 端口必须声明 exclusiveGroupId",
            meta,
            String.format("outputPorts[%d].exclusiveGroupId", port.getIndex()),
            "output",
            port.getIndex()
        )));

    getOutputPorts().stream()
        .filter(port -> StringUtils.isNotBlank(port.getExclusiveGroupId()))
        .collect(Collectors.groupingBy(FlowNodePort::getExclusiveGroupId))
        .forEach((groupId, ports) -> {
          var defaultCount = ports.stream()
              .filter(port -> Boolean.TRUE.equals(port.isDefaultBranch()))
              .count();
          if (defaultCount > 1) {
            var firstDefaultBranch = ports.stream()
                .filter(port -> Boolean.TRUE.equals(port.isDefaultBranch()))
                .findFirst();
            addError(new NodeDesignException(
                "同一 exclusiveGroupId 只能存在一个 defaultBranch",
                meta,
                firstDefaultBranch
                    .map(port -> String.format("outputPorts[%d].defaultBranch", port.getIndex()))
                    .orElse("outputPorts"),
                "output",
                firstDefaultBranch.map(FlowNodePort::getIndex).orElse(null)
            ));
          }
        });
  }
}
