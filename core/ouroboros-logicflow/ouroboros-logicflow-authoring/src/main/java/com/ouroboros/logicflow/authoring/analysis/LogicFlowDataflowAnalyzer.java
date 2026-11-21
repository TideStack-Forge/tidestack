package com.ouroboros.logicflow.authoring.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.ouroboros.codeintelligence.analysis.ExpressionTypeQuery;
import com.ouroboros.codeintelligence.type.TypeShape;
import com.ouroboros.logicflow.model.*;
import com.ouroboros.logicflow.authoring.provider.LogicFlowNodeDefineProvider;
import com.ouroboros.util.SPI;

/** Draft-only DAG analysis; does not construct executable flow or node instances. */
public final class LogicFlowDataflowAnalyzer {
  private final Map<String, FlowNodeAnalyzer> analyzers;

  public LogicFlowDataflowAnalyzer() { this(discoveredAnalyzers()); }

  public LogicFlowDataflowAnalyzer(Collection<FlowNodeAnalyzer> implementations) {
    var index = new LinkedHashMap<String, FlowNodeAnalyzer>();
    for (var analyzer : implementations) {
      for (var type : analyzer.nodeTypes()) {
        if (index.putIfAbsent(type.toLowerCase(Locale.ROOT), analyzer) != null) throw new IllegalArgumentException("Duplicate node analyzer: " + type);
      }
    }
    analyzers = Map.copyOf(index);
  }

  public static List<FlowNodeAnalyzer> discoveredAnalyzers() {
    return SPI.getCachedSortedServiceStream(FlowNodeAnalyzer.class).toList();
  }

  public LogicFlowDataflowAnalysis analyze(FlowModelMeta flow, List<TypeShape> entryInputs, Map<String, TypeShape> globals, ExpressionTypeQuery expressions) {
    return analyze(flow, entryInputs, globals, expressions, FlowTypeContracts.empty());
  }

  public LogicFlowDataflowAnalysis analyze(FlowModelMeta flow, List<TypeShape> entryInputs, Map<String, TypeShape> globals,
      ExpressionTypeQuery expressions, FlowTypeContracts contracts) {
    if (flow.getNodes().size() > 2000 || flow.getConnections().size() > 10000) throw new IllegalArgumentException("Flow analysis graph budget exceeded");
    var nodes = new LinkedHashMap<String, FlowNodeMeta>();
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    for (var node : flow.getNodes()) {
      if (node.getName() == null || node.getName().isBlank() || nodes.putIfAbsent(node.getName(), node) != null) {
        diagnostics.add(diagnostic(node.getName(), "duplicate-node", "Node name is absent or duplicated"));
      }
      if (Boolean.FALSE.equals(node.isEnabled())) {
        diagnostics.add(diagnostic(node.getName(), "disabled-node", "Disabled nodes are excluded from runtime propagation"));
      }
    }
    var edges = flow.getConnections().stream().filter(edge -> validEdge(edge, nodes, diagnostics)).filter(edge -> {
      var source = nodes.get(edge.getSourceNode());
      var target = nodes.get(edge.getTargetNode());
      if (Boolean.FALSE.equals(source.isEnabled()) || Boolean.FALSE.equals(target.isEnabled())) {
        diagnostics.add(diagnostic(edge.getTargetNode(), "disabled-connection", "Connection touching a disabled node is excluded"));
        return false;
      }
      return true;
    }).toList();
    var remaining = new LinkedHashMap<String, Integer>();
    var inboundByNode = new LinkedHashMap<String, List<FlowConnectionMeta>>();
    var outboundByNode = new LinkedHashMap<String, List<FlowConnectionMeta>>();
    nodes.keySet().forEach(name -> remaining.put(name, 0));
    edges.forEach(edge -> {
      remaining.computeIfPresent(edge.getTargetNode(), (name, count) -> count + 1);
      inboundByNode.computeIfAbsent(edge.getTargetNode(), ignored -> new ArrayList<>()).add(edge);
      outboundByNode.computeIfAbsent(edge.getSourceNode(), ignored -> new ArrayList<>()).add(edge);
    });
    var queue = new ArrayDeque<String>();
    remaining.forEach((name, count) -> { if (count == 0) queue.add(name); });
    var results = new LinkedHashMap<String, NodeAnalysis>();
    var definitions = SPI.getCachedSortedServiceStream(LogicFlowNodeDefineProvider.class).flatMap(provider -> provider.getNodeDefines().stream()).toList();
    while (!queue.isEmpty()) {
      var name = queue.remove();
      var node = nodes.get(name);
      var inbound = inboundByNode.getOrDefault(name, List.of());
      var minimum = definitions.stream().filter(def -> def.getType() != null && node.getType() != null
          && def.getType().equalsIgnoreCase(node.getType())).findFirst()
          .map(def -> def.getPortRules()).map(rules -> rules.get("input")).map(rule -> rule.get("min"))
          .filter(Number.class::isInstance).map(Number.class::cast).map(Number::intValue).orElse(1);
      var count = Math.max(node.getInputPorts().size(), Math.max(minimum, inbound.stream().mapToInt(edge -> edge.getTargetInputPort() + 1).max().orElse(0)));
      if (Objects.equals(name, flow.getEntryNode())) count = Math.max(count, entryInputs.size());
      var inputs = new ArrayList<PortShape>();
      for (int i = 0; i < count; i++) {
        final int port = i;
        var sources = inbound.stream().filter(edge -> edge.getTargetInputPort() == port).map(edge ->
            results.get(edge.getSourceNode()).outputs().getOrDefault(edge.getSourceOutputPort(), PortShape.always(TypeShape.unknown("missing-output")))).toList();
        if (!sources.isEmpty()) {
          var shape = sources.stream().map(PortShape::shape).reduce(TypeShape::merge).orElseThrow();
          inputs.add(new PortShape(shape, sources.stream().allMatch(source -> "always".equals(source.availability())) ? "always" : "conditional"));
        } else if (Objects.equals(name, flow.getEntryNode()) && i < entryInputs.size()) inputs.add(PortShape.always(entryInputs.get(i)));
        else inputs.add(PortShape.always(TypeShape.unknown("unconnected-input:" + i)));
      }
      var analyzer = analyzers.get(String.valueOf(node.getType()).toLowerCase(Locale.ROOT));
      NodeAnalysis result;
      if (Boolean.FALSE.equals(node.isEnabled())) {
        result = new NodeAnalysis(Map.of(0, PortShape.always(TypeShape.unknown("disabled-node"))), Map.of(), List.of());
      } else if (analyzer == null) {
        result = new NodeAnalysis(Map.of(0, PortShape.always(TypeShape.unknown("missing-analyzer:" + node.getType()))), Map.of(), List.of(diagnostic(name, "missing-analyzer", "No static analyzer for " + node.getType())));
      } else {
        try {
          result = analyzer.analyze(new NodeAnalysisInput(node, inputs, globals, contracts), expressions);
        } catch (RuntimeException exception) {
          result = new NodeAnalysis(Map.of(0, PortShape.always(TypeShape.unknown("analyzer-failure"))), Map.of(),
              List.of(diagnostic(name, "analyzer-failure", "Node analyzer failed: " + exception.getClass().getSimpleName())));
        }
      }
      result = new NodeAnalysis(result.outputs(), result.codeScopes(), result.diagnostics(), inputs);
      results.put(name, result);
      diagnostics.addAll(result.diagnostics());
      for (var edge : outboundByNode.getOrDefault(name, List.of())) {
        if (!result.outputs().containsKey(edge.getSourceOutputPort())) {
          diagnostics.add(diagnostic(name, "invalid-output-port", "Node analysis did not declare output port " + edge.getSourceOutputPort()));
        }
        if (remaining.compute(edge.getTargetNode(), (key, value) -> value - 1) == 0) queue.add(edge.getTargetNode());
      }
    }
    nodes.forEach((name, node) -> {
      if (!results.containsKey(name)) {
        var issue = diagnostic(name, "cyclic-dependency", "Cyclic draft or dependency on a cyclic node");
        diagnostics.add(issue);
        var analyzer = analyzers.get(String.valueOf(node.getType()).toLowerCase(Locale.ROOT));
        var count = Math.max(1, node.getInputPorts().size());
        var inputs = java.util.stream.IntStream.range(0, count).mapToObj(index -> PortShape.always(TypeShape.unknown("cyclic-dependency"))).toList();
        Map<String, CodeScope> scopes = Map.of();
        if (analyzer != null) {
          try {
            scopes = analyzer.analyze(new NodeAnalysisInput(node, inputs, globals, contracts), expressions).codeScopes();
          } catch (RuntimeException exception) {
            diagnostics.add(diagnostic(name, "analyzer-failure", "Node analyzer failed: " + exception.getClass().getSimpleName()));
          }
        }
        results.put(name, new NodeAnalysis(Map.of(0, PortShape.always(TypeShape.unknown("cyclic-dependency"))), scopes, List.of(issue)));
      }
    });
    var reachable = new java.util.LinkedHashSet<String>();
    var terminals = new java.util.LinkedHashSet<String>();
    var pending = new ArrayDeque<String>();
    if (flow.getEntryNode() != null) pending.add(flow.getEntryNode());
    while (!pending.isEmpty()) {
      var name = pending.remove();
      var node = nodes.get(name);
      if (node == null || Boolean.FALSE.equals(node.isEnabled()) || !reachable.add(name)) continue;
      var definition = definitions.stream().filter(def -> def.getType() != null && def.getType().equalsIgnoreCase(node.getType())).findFirst();
      boolean successfulTerminal = definition.map(def -> "successful".equalsIgnoreCase(def.getTerminalKind())).orElse(false);
      if (Objects.equals(name, flow.getExitNode()) || successfulTerminal) terminals.add(name);
      if (Objects.equals(name, flow.getExitNode()) || definition.map(def -> Boolean.TRUE.equals(def.getIsTerminal())).orElse(false)) continue;
      for (var edge : outboundByNode.getOrDefault(name, List.of())) {
        if (results.get(name).outputs().containsKey(edge.getSourceOutputPort())) pending.add(edge.getTargetNode());
      }
    }
    return new LogicFlowDataflowAnalysis(results, diagnostics, reachable, terminals);
  }

  private static boolean validEdge(FlowConnectionMeta edge, Map<String, FlowNodeMeta> nodes, List<AnalysisDiagnostic> diagnostics) {
    if (!nodes.containsKey(edge.getSourceNode()) || !nodes.containsKey(edge.getTargetNode()) || edge.getSourceOutputPort() == null
        || edge.getTargetInputPort() == null || edge.getSourceOutputPort() < 0 || edge.getTargetInputPort() < 0
        || edge.getSourceOutputPort() > 1024 || edge.getTargetInputPort() > 1024) {
      diagnostics.add(diagnostic(edge.getTargetNode(), "invalid-connection", "Connection refers to a missing node or invalid port"));
      return false;
    }
    return true;
  }

  private static AnalysisDiagnostic diagnostic(String node, String code, String message) { return new AnalysisDiagnostic(node, "", code, message); }
}
