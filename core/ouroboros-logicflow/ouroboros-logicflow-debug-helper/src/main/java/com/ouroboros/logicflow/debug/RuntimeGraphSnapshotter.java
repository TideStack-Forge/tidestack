package com.ouroboros.logicflow.debug;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import com.ouroboros.logicflow.diagnostic.FlowDebugGraph;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.util.Json;

final class RuntimeGraphSnapshotter {
  FlowDebugGraph capture(FlowModel model) {
    if (model.getNodes().size() > 2000 || model.getConnections().size() > 10000) {
      throw new IllegalArgumentException("Runtime graph exceeds snapshot capacity");
    }
    var nodes = model.getNodes().stream().map(node -> new FlowDebugGraph.Node(
        node.getName(), node.getType(), bounded(node.getDescription()), node.getInputPorts().size(), node.getOutputPorts().size())).toList();
    var connections = model.getConnections().stream().map(connection -> new FlowDebugGraph.Connection(
        connection.getSourceName(), connection.getSourceOutputPort(), connection.getTargetName(), connection.getTargetInputPort())).toList();
    String revision = model.getRevision();
    boolean exact = revision != null && !revision.equals(model.getName());
    var graph = new FlowDebugGraph("", model.getName(), revision, exact, name(model.getEntryNode()), name(model.getExitNode()),
        name(model.getErrorEntryNode()), nodes, connections);
    byte[] bytes = Json.tryToJsonString(graph).get().getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 24576) {
      throw new IllegalArgumentException("Runtime graph exceeds event snapshot capacity");
    }
    try {
      String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      return new FlowDebugGraph(id, graph.flowName(), graph.revision(), graph.exactRevision(), graph.entryNode(), graph.exitNode(),
          graph.errorEntryNode(), graph.nodes(), graph.connections());
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 is unavailable", failure);
    }
  }

  private String name(FlowNode node) {
    return node == null ? null : node.getName();
  }

  private String bounded(String value) {
    return value == null || value.length() <= 256 ? value : value.substring(0, 256);
  }
}
