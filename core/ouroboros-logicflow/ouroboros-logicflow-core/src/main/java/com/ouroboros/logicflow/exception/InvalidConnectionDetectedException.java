package com.ouroboros.logicflow.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.model.FlowConnectionMeta;
import com.ouroboros.logicflow.model.FlowModelMeta;

public class InvalidConnectionDetectedException extends FlowDesignException {
  private final List<String> connectionDescriptions;
  private final List<FlowConnectionMeta> connections;

  public InvalidConnectionDetectedException(FlowModelMeta model, List<String> connectionDescriptions) {
    super(buildMessage(connectionDescriptions), model);
    this.connectionDescriptions = connectionDescriptions == null ? new ArrayList<>() : new ArrayList<>(connectionDescriptions);
    this.connections = new ArrayList<>();
  }

  public InvalidConnectionDetectedException(
      FlowModelMeta model,
      List<String> connectionDescriptions,
      List<FlowConnectionMeta> connections
  ) {
    super(buildMessage(connectionDescriptions), model);
    this.connectionDescriptions = connectionDescriptions == null ? new ArrayList<>() : new ArrayList<>(connectionDescriptions);
    this.connections = connections == null
        ? new ArrayList<>()
        : connections.stream().map(FlowConnectionMeta::clone).collect(Collectors.toList());
  }

  public List<String> getConnectionDescriptions() {
    return new ArrayList<>(connectionDescriptions);
  }

  public List<FlowConnectionMeta> getConnections() {
    return connections.stream().map(FlowConnectionMeta::clone).collect(Collectors.toList());
  }

  private static String buildMessage(List<String> connectionDescriptions) {
    if (connectionDescriptions == null || connectionDescriptions.isEmpty()) {
      return "连线端口存在错误";
    }
    var summarized = connectionDescriptions.stream()
        .limit(5)
        .collect(Collectors.joining("; "));
    if (connectionDescriptions.size() <= 5) {
      return "连线端口存在错误: " + summarized;
    }
    return "连线端口存在错误: " + summarized + String.format(" (and %d more)", connectionDescriptions.size() - 5);
  }
}
