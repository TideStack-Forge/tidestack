package com.ouroboros.logicflow.diagnostic;

import java.util.List;

/** Immutable runtime graph; endpoints use runtime node names and zero-based ports. */
public record FlowDebugGraph(String id, String flowName, String revision, boolean exactRevision,
                             String entryNode, String exitNode, String errorEntryNode,
                             List<Node> nodes, List<Connection> connections) {
  public FlowDebugGraph {
    nodes = List.copyOf(nodes);
    connections = List.copyOf(connections);
  }

  public record Node(String name, String type, String description, int inputPorts, int outputPorts) {}
  public record Connection(String sourceNode, int sourceOutputPort, String targetNode, int targetInputPort) {}
}
