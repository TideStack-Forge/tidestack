package com.ouroboros.logicflow.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.logicflow.model.FlowConnection;
import com.ouroboros.logicflow.model.DataTypeEnum;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowNodePort;
import com.ouroboros.logicflow.nodes.AbstractTerminalNode;

public final class LogicFlowUtil {
  private LogicFlowUtil() {
  }


  /**
   * 拓扑排序
   *
   * @param flowModel
   * @return
   */
  public static List<FlowNode> topologicalSort(FlowModel flowModel) {
    var connections = flowModel.getConnections().stream()
        .filter(LogicFlowUtil::isActiveConnection)
        .collect(Collectors.toList());
    var nodes = flowModel.getNodes().stream()
        .filter(FlowNode::isEnabled)
        // 排序，将异步节点放在同步节点之前
        .sorted((node1, node2) -> {
          if (node1.isAsync() && !node2.isAsync()) {
            return 1;
          } else if (!node1.isAsync() && node2.isAsync()) {
            return -1;
          } else {
            return 0;
          }
        })
        .collect(Collectors.toList());
    List<FlowNode> result = new ArrayList<>();

    // 如果没有连接，则直接返回节点列表
    if (connections.isEmpty()) {
      result.addAll(nodes);
      return result;
    }
    // 拓扑排序
    // 1. 找到入度为0的节点
    // 2. 将入度为0的节点加入到拓扑排序结果中
    // 3. 将入度为0的节点的所有出度节点的入度减1
    // 4. 重复1-3，直到所有节点都加入到拓扑排序结果中
    // 5. 如果存在入度不为0的节点，则说明存在环，拓扑排序失败
    while (!connections.isEmpty()) {
      final var connectionsForStream = connections;
      // 1. 找到入度为0的节点
      final var nextNodes = nodes.stream()
          .filter(node -> connectionsForStream.stream().noneMatch(connection -> connection.getTargetNode() == node))
          .collect(Collectors.toList());

      // 防止死循环
      if (nextNodes.isEmpty()) {
        throw new IllegalStateException("拓扑排序失败");
      }
      // 2. 将入度为0的节点加入到拓扑排序结果中
      result.addAll(nextNodes);

      // 3. 将入度为0的节点的所有出度节点的入度减1
      connections = connections.stream()
          .filter(connection -> nextNodes.stream().noneMatch(node -> node == connection.getSourceNode()))
          .collect(Collectors.toList());

      // 剔除已经加入到拓扑排序结果中的节点，开始下一轮循环
      nodes = nodes.stream()
          .filter(node -> !nextNodes.contains(node))
          .collect(Collectors.toList());
    }
    // 5. 最后剩下的节点，都是入度为0的节点
    if (!nodes.isEmpty()) {
      result.addAll(nodes);
    }
    return result;
  }

  /**
   * 检查节点输入是否由上游连线或所在 scope 的外部输入提供
   *
   * @param flowModel 逻辑流模型
   * @return true：根节点可接收外部输入，其余节点的输入端口全部连接；false：存在部分连接的节点
   */
  public static boolean checkAllInputPortsConnected(FlowModel flowModel) {
    // 没有有效输入连线的根节点按端口序号接收所在 scope 的外部输入。
    List<FlowNode> externalInputRoots = flowModel.getNodes().stream()
        .filter(LogicFlowUtil::isActiveNode)
        .filter(node -> node.getIncomingConnections().stream().noneMatch(LogicFlowUtil::isActiveConnection))
        .collect(Collectors.toList());

    return flowModel.getNodes().stream()
        .filter(LogicFlowUtil::isActiveNode)
        .allMatch(node -> {
          var inPortsCount = node.getInputPorts().size();
          var incomingConnections = node.getIncomingConnections().stream()
              .filter(LogicFlowUtil::isActiveConnection)
              .collect(Collectors.toList());
          var allInPortsConnected = IntStream.range(0, inPortsCount)
              .allMatch(index -> incomingConnections.stream().anyMatch(connection -> connection.getTargetInputPort() == index));
          return (externalInputRoots.stream().anyMatch(root -> root.getName().equals(node.getName()))) || allInPortsConnected;
        });
  }

  public static List<FlowNode> getSuccessfulTerminalNodes(FlowModel flowModel) {
    return getSuccessfulTerminalNodes(flowModel, flowModel.getNodes().stream()
        .filter(FlowNode::isEnabled)
        .collect(Collectors.toSet()), flowModel.getExitNode());
  }

  public static List<FlowNode> getSuccessfulTerminalNodes(FlowModel flowModel, Set<FlowNode> scopeNodes, FlowNode explicitExitNode) {
    var terminals = new LinkedHashSet<FlowNode>();
    if (explicitExitNode != null && explicitExitNode.isEnabled() && scopeNodes.contains(explicitExitNode)) {
      terminals.add(explicitExitNode);
    }
    scopeNodes.stream()
        .filter(AbstractTerminalNode.class::isInstance)
        .forEach(terminals::add);
    return new ArrayList<>(terminals);
  }

  public static boolean hasSuccessfulTerminal(FlowModel flowModel) {
    return !getSuccessfulTerminalNodes(flowModel).isEmpty();
  }

  public static boolean hasSuccessfulTerminal(FlowModel flowModel, Set<FlowNode> scopeNodes, FlowNode explicitExitNode) {
    return !getSuccessfulTerminalNodes(flowModel, scopeNodes, explicitExitNode).isEmpty();
  }

  public static boolean checkSuccessfulTerminalExclusivity(FlowModel flowModel) {
    return checkSuccessfulTerminalExclusivity(flowModel, flowModel.getNodes().stream()
        .filter(FlowNode::isEnabled)
        .collect(Collectors.toSet()), flowModel.getExitNode());
  }

  public static boolean checkSuccessfulTerminalExclusivity(FlowModel flowModel, Set<FlowNode> scopeNodes, FlowNode explicitExitNode) {
    var terminals = getSuccessfulTerminalNodes(flowModel, scopeNodes, explicitExitNode);
    if (terminals.size() < 2) {
      return true;
    }
    for (int i = 0; i < terminals.size(); i++) {
      var left = terminals.get(i);
      for (int j = i + 1; j < terminals.size(); j++) {
        var right = terminals.get(j);
        if (left == right) {
          continue;
        }
        if (canReach(left, right) || canReach(right, left)) {
          return false;
        }
        if (!isSuccessfulTerminalPairExclusive(left, right)) {
          return false;
        }
      }
    }
    return true;
  }

  public static Set<FlowNode> getReachableNodes(FlowNode startNode) {
    var reachable = new LinkedHashSet<FlowNode>();
    collectReachableNodes(startNode, reachable);
    return reachable;
  }

  public static Set<FlowNode> getScopeNodes(FlowNode startNode) {
    var scopeNodes = getReachableNodes(startNode);
    new ArrayList<>(scopeNodes).forEach(node -> collectUpstreamScopeNodes(startNode, node, scopeNodes));
    return scopeNodes;
  }

  public static Set<FlowNode> getUnreachableEnabledNodes(FlowModel flowModel, Set<FlowNode> reachableNodes) {
    var unreachable = flowModel.getNodes().stream()
        .filter(FlowNode::isEnabled)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    unreachable.removeAll(reachableNodes);
    return unreachable;
  }

  public static Set<FlowNode> getInvalidLeafNodes(Set<FlowNode> scopeNodes, FlowNode explicitExitNode) {
    return scopeNodes.stream()
        .filter(FlowNode::isEnabled)
        .filter(node -> !node.getOutputPorts().isEmpty())
        .filter(node -> node.getOutgoingConnections().isEmpty())
        .filter(node -> node != explicitExitNode)
        .filter(node -> !(node instanceof AbstractTerminalNode))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public static Set<FlowNode> getMainFlowNodes(FlowModel flowModel) {
    if (flowModel.getErrorEntryNode() == null) {
      if (flowModel.getEntryNode() == null) {
        return flowModel.getNodes().stream()
            .filter(FlowNode::isEnabled)
            .collect(Collectors.toCollection(LinkedHashSet::new));
      }
      return getScopeNodes(flowModel.getEntryNode());
    }
    return getScopeNodes(flowModel.getEntryNode());
  }

  public static Set<FlowNode> getErrorFlowNodes(FlowModel flowModel) {
    if (flowModel.getErrorEntryNode() == null) {
      return new LinkedHashSet<>();
    }
    return getScopeNodes(flowModel.getErrorEntryNode());
  }

  public static Optional<String> validateFlowScopes(FlowModel flowModel) {
    return validateFlowScopeIssue(flowModel).map(FlowScopeValidationIssue::getMessage);
  }

  public static Optional<FlowScopeValidationIssue> validateFlowScopeIssue(FlowModel flowModel) {
    var errorEntryNode = flowModel.getErrorEntryNode();
    if (errorEntryNode == null) {
      return Optional.empty();
    }
    if (errorEntryNode == flowModel.getEntryNode()) {
      return Optional.of(new FlowScopeValidationIssue("异常入口节点不能与主入口节点相同",
          collectNodeNames(errorEntryNode)));
    }
    if (flowModel.getExitNode() != null && errorEntryNode == flowModel.getExitNode()) {
      return Optional.of(new FlowScopeValidationIssue("异常入口节点不能与出口节点相同",
          collectNodeNames(errorEntryNode)));
    }

    var enabledNodes = flowModel.getNodes().stream()
        .filter(FlowNode::isEnabled)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    var mainScopeNodes = getScopeNodes(flowModel.getEntryNode());
    var errorScopeNodes = getScopeNodes(errorEntryNode);

    var overlap = new LinkedHashSet<>(mainScopeNodes);
    overlap.retainAll(errorScopeNodes);
    if (!overlap.isEmpty()) {
      return Optional.of(new FlowScopeValidationIssue("存在同时属于主流程和异常流程的节点",
          collectNodeNames(overlap)));
    }

    var covered = new LinkedHashSet<>(mainScopeNodes);
    covered.addAll(errorScopeNodes);
    var unreachable = new LinkedHashSet<>(enabledNodes);
    unreachable.removeAll(covered);
    if (!unreachable.isEmpty()) {
      return Optional.of(new FlowScopeValidationIssue("存在不属于主流程或异常流程的已启用节点",
          collectNodeNames(unreachable)));
    }

    return Optional.empty();
  }

  private static List<String> collectNodeNames(FlowNode node) {
    return node == null ? new ArrayList<>() : collectNodeNames(Collections.singleton(node));
  }

  private static List<String> collectNodeNames(Set<FlowNode> nodes) {
    return nodes.stream()
        .filter(Objects::nonNull)
        .map(FlowNode::getName)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public static final class FlowScopeValidationIssue {
    private final String message;
    private final List<String> nodeNames;

    public FlowScopeValidationIssue(String message, List<String> nodeNames) {
      this.message = message;
      this.nodeNames = nodeNames == null ? new ArrayList<>() : new ArrayList<>(nodeNames);
    }

    public String getMessage() {
      return message;
    }

    public List<String> getNodeNames() {
      return new ArrayList<>(nodeNames);
    }
  }

  /**
   * 检查输入端口互斥
   * <p>连接同一个输入端口的所有上游路径必须是互斥的，即连到同一个显式声明 selective-output metadata 的互斥端口组时才允许复用</p>
   * <ol>
   *   <li>先找出所有同一个输入端口有多个上游路径的节点+端口 </li>
   *   <li>对于每个节点+输入端口，找出所有上游路径 </li>
   *   <li>检查每个节点+输入端口的所有上游路径是否互斥 </li>
   *   <li>如果所有路径都互斥，则返回true，否则返回false </li>
   * </ol>
   *
   * @param flowModel 逻辑流模型
   * @return true：所有输入端口互斥；false：存在输入端口不互斥
   */
  public static boolean checkInputPortConflicts(FlowModel flowModel) {
    var connections = flowModel.getConnections().stream()
        .filter(LogicFlowUtil::isActiveConnection)
        .collect(Collectors.toList());
    var nodes = flowModel.getNodes().stream()
        .filter(LogicFlowUtil::isActiveNode)
        .collect(Collectors.toList());
    // 连接同一个输入端口的所有上游路径必须是互斥的，即必须最终连接到同一个显式 opt-in 的互斥输出组
    // 1. 先找出所有同一个输入端口有多个上游路径的节点+端口
    // 2. 对于每个节点+输入端口，找出所有上游路径
    // 3. 检查每个节点+输入端口的所有上游路径是否互斥

    // 1. 先找出所有同一个端口有多个上游路径的节点
    var nodesWithMultipleInConnections = nodes.stream()
        .map(node -> {
          var incoming = node.getIncomingConnections().stream()
              .filter(LogicFlowUtil::isWellFormedActiveConnection)
              .collect(Collectors.collectingAndThen(
                  Collectors.groupingBy(FlowConnection::getTargetInputPort),
                  lst -> lst.entrySet().stream()
                      .filter(entry -> entry.getValue().size() > 1)
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v2))));
          return Tuple.<FlowNode, Map<Integer, List<FlowConnection>>>of(node, incoming);
        })
        .filter(tuple -> !tuple._2().isEmpty())
        .flatMap(tuple -> tuple._2().keySet().stream().map(port -> Tuple.of(tuple._1(), port)))
        .collect(Collectors.toList());

    // 2. 对于每个节点+输入端口，找出所有上游路径
    var paths = nodesWithMultipleInConnections.stream()
        .map(tuple -> {
          var node = tuple._1();
          var port = tuple._2();
          var pathList = getUpstreamNodesPaths(tuple);
          return Tuple.<FlowNode, Integer, List<List<Tuple2<FlowNode, Integer>>>>of(node, port, pathList);
        })
        .collect(Collectors.toList());

    // 3. 检查每个节点+输入端口的所有上游路径是否互斥
    return paths.stream().allMatch(tuple -> {
      var pathList = tuple._3();
      // 只有一个路径，必然互斥
      if (pathList.size() < 2) {
        return true;
      }
      // 检查每个路径是否互斥
      for (int i = 0; i < pathList.size(); i++) {
        var path1 = pathList.get(i);
        for (int j = i + 1; j < pathList.size(); j++) {
          var path2 = pathList.get(j);
          // 不互斥则代表有冲突，直接返回false
          if (!isPathMutex(path1, path2)) {
            return false;
          }
        }
      }
      return true;
    });
  }

  /**
   * 判断两个路径是否互斥
   * <ul>
   *   <li>如果上游路径中有相同的 opt-in selective 节点，且输出端口属于同一 exclusiveGroupId，则互斥</li>
   *   <li>如果上游路径中没有这样的节点，则非互斥</li>
   * </ul>
   *
   * @param path1 路径1
   * @param path2 路径2
   * @return true：互斥；false：非互斥
   */
  private static boolean isPathMutex(List<Tuple2<FlowNode, Integer>> path1, List<Tuple2<FlowNode, Integer>> path2) {
    for (var tuple1 : path1) {
      var node1 = tuple1._1();
      var port1 = tuple1._2();
      for (var tuple2 : path2) {
        var node2 = tuple2._1();
        var port2 = tuple2._2();
        if (node1 == node2 && isExclusivePortPair(node1, port1, port2)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isExclusivePortPair(FlowNode node, Integer leftPortIndex, Integer rightPortIndex) {
    if (node == null || !Boolean.TRUE.equals(node.supportsSelectiveOutputMetadata()) || Objects.equals(leftPortIndex, rightPortIndex)) {
      return false;
    }
    var outputPorts = node.getOutputPorts();
    if (leftPortIndex == null || rightPortIndex == null
        || leftPortIndex < 0 || rightPortIndex < 0
        || leftPortIndex >= outputPorts.size() || rightPortIndex >= outputPorts.size()) {
      return false;
    }
    var leftPort = outputPorts.get(leftPortIndex);
    var rightPort = outputPorts.get(rightPortIndex);
    return hasExclusiveGroupId(leftPort) && Objects.equals(leftPort.getExclusiveGroupId(), rightPort.getExclusiveGroupId());
  }

  private static boolean hasExclusiveGroupId(FlowNodePort port) {
    return port != null && port.getExclusiveGroupId() != null && !port.getExclusiveGroupId().trim().isEmpty();
  }

  private static boolean isSuccessfulTerminalPairExclusive(FlowNode left, FlowNode right) {
    var leftPaths = collectSuccessfulTerminalPaths(left);
    var rightPaths = collectSuccessfulTerminalPaths(right);
    if (leftPaths.isEmpty() || rightPaths.isEmpty()) {
      return false;
    }
    for (var leftPath : leftPaths) {
      for (var rightPath : rightPaths) {
        if (!isPathMutex(leftPath, rightPath)) {
          return false;
        }
      }
    }
    return true;
  }

  private static List<List<Tuple2<FlowNode, Integer>>> collectSuccessfulTerminalPaths(FlowNode node) {
    var inputPorts = node.getInputPorts().size();
    if (inputPorts == 0) {
      return new ArrayList<>();
    }
    return IntStream.range(0, inputPorts)
        .mapToObj(index -> getUpstreamNodesPaths(Tuple.of(node, index)))
        .flatMap(List::stream)
        .collect(Collectors.toList());
  }

  private static boolean canReach(FlowNode source, FlowNode target) {
    return canReach(source, target, new HashSet<>());
  }

  private static boolean canReach(FlowNode source, FlowNode target, Set<FlowNode> visited) {
    if (!isActiveNode(source) || !visited.add(source)) {
      return false;
    }
    for (var connection : source.getOutgoingConnections().stream().filter(LogicFlowUtil::isActiveConnection).collect(Collectors.toList())) {
      var next = connection.getTargetNode();
      if (next == null) {
        continue;
      }
      if (next == target) {
        return true;
      }
      if (canReach(next, target, visited)) {
        return true;
      }
    }
    return false;
  }

  private static void collectReachableNodes(FlowNode node, Set<FlowNode> reachable) {
    if (node == null || !node.isEnabled() || !reachable.add(node)) {
      return;
    }
    node.getOutgoingConnections().stream()
        .filter(LogicFlowUtil::isActiveConnection)
        .map(FlowConnection::getTargetNode)
        .filter(Objects::nonNull)
        .forEach(next -> collectReachableNodes(next, reachable));
  }

  private static void collectUpstreamScopeNodes(FlowNode scopeEntryNode, FlowNode node, Set<FlowNode> scopeNodes) {
    if (node == null || !node.isEnabled() || node == scopeEntryNode) {
      return;
    }
    node.getIncomingConnections().stream()
        .filter(LogicFlowUtil::isActiveConnection)
        .map(FlowConnection::getSourceNode)
        .filter(Objects::nonNull)
        .forEach(previous -> {
          if (scopeNodes.add(previous)) {
            collectUpstreamScopeNodes(scopeEntryNode, previous, scopeNodes);
          }
        });
  }

  /**
   * 获取节点+输入端口的所有上游路径
   *
   * @param nodePort 节点 + 输入端口
   * @return 所有上游路径列表
   */
  public static List<List<Tuple2<FlowNode, Integer>>> getUpstreamNodesPaths(Tuple2<FlowNode, Integer> nodePort) {
    return getUpstreamNodesPathsHelper(nodePort, new ArrayList<>());
  }

  /**
   * 递归获取节点+输入端口的所有上游路径
   *
   * @param nodePort 节点 + 输入端口
   * @param path     当前路径
   * @return 所有上游路径列表
   */
  private static List<List<Tuple2<FlowNode, Integer>>> getUpstreamNodesPathsHelper(Tuple2<FlowNode, Integer> nodePort, List<Tuple2<FlowNode, Integer>> path) {
    final var node = nodePort._1();
    final var port = nodePort._2();
    final var incomingConnections = node.getIncomingConnections().stream()
        .filter(LogicFlowUtil::isActiveConnection)
        .filter(connection -> Objects.equals(connection.getTargetInputPort(), port))
        .collect(Collectors.toList());

    List<List<Tuple2<FlowNode, Integer>>> result = new ArrayList<>();
    if (incomingConnections.isEmpty()) {
      // 当没有上游节点时，返回单独的路径
      result.add(path);
      return result;
    }

    // 对每个上游路径执行递归调用
    for (var connection : incomingConnections) {
      var sourceNode = connection.getSourceNode();
      var sourcePort = connection.getSourceOutputPort();
      var newPath = new ArrayList<>(path);
      newPath.add(0, Tuple.of(sourceNode, sourcePort));
      result.addAll(getUpstreamNodesPathsHelper(Tuple.of(sourceNode, sourcePort), newPath));
    }
    return result;
  }

  /**
   * 检查竞态条件
   * 同一 selective-output 互斥组的输出端口不能连接到同一个节点的不同端口
   * <ol>
   *   <li>找出所有条件选择节点</li>
   *   <li>找出所有条件选择节点每个输出端口的下游路径</li>
   *   <li>检查是否存在不同输出端口连接到同一个节点的不同输入端口的情况</li>
   * <ol/>
   *
   * @param flowModel 逻辑流模型
   * @return true：不存在竞态条件；false：存在竞态条件
   */
  public static boolean checkRacingCondition(FlowModel flowModel) {
    var nodes = flowModel.getNodes().stream().filter(FlowNode::isEnabled).collect(Collectors.toList());
    // 1. 找出所有显式声明 selective-output metadata 的节点
    var selectiveNodes = nodes.stream()
        .filter(node -> Boolean.TRUE.equals(node.supportsSelectiveOutputMetadata()))
        .collect(Collectors.toList());

    // 2. 找出所有互斥输出组每个端口的下游路径
    var nodeOutPaths = selectiveNodes.stream()
        .flatMap(node -> collectExclusiveGroupPaths(node).stream())
        .collect(Collectors.toList());

    // 3. 检查同一互斥输出组里是否存在不同输出端口连接到同一个节点的不同输入端口的情况
    return nodeOutPaths.stream()
        .allMatch(tuple -> {
          var pathMap = tuple._2();
          return checkRacingConditionHelper(pathMap);
        });
  }

  private static List<Tuple2<FlowNode, Map<Integer, List<List<Tuple2<FlowNode, Integer>>>>>> collectExclusiveGroupPaths(FlowNode node) {
    return node.getOutputPorts().stream()
        .filter(LogicFlowUtil::hasExclusiveGroupId)
        .collect(Collectors.groupingBy(FlowNodePort::getExclusiveGroupId, Collectors.mapping(FlowNodePort::getIndex, Collectors.toList())))
        .values().stream()
        .filter(portIndexes -> portIndexes.size() > 1)
        .map(portIndexes -> {
          var portPaths = portIndexes.stream()
              .collect(Collectors.toMap(port -> port, port -> getDownstreamNodesPaths(Tuple.of(node, port)), (v1, v2) -> v2));
          return Tuple.of(node, portPaths);
        })
        .collect(Collectors.toList());
  }

  /**
   * 检查竞态条件
   *
   * @param pathMap 路径映射，key: 节点输出端口，value: 节点输出端口的下游路径
   * @return true：不存在竞态条件；false：存在竞态条件
   */
  private static boolean checkRacingConditionHelper(Map<Integer, List<List<Tuple2<FlowNode, Integer>>>> pathMap) {
    var portPaths = pathMap.values();
    for (var paths1 : portPaths) {
      if (paths1.isEmpty()) {
        continue;
      }
      for (var paths2 : portPaths) {
        if (paths1 == paths2) {
          continue;
        }
        if (paths2.isEmpty()) {
          continue;
        }
        for (var path1 : paths1) {
          for (var path2 : paths2) {
            if (isPathRacing(path1, path2)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  /**
   * 检查两个路径是否存在竞态条件
   *
   * @param path1 路径1
   * @param path2 路径2
   * @return true：存在竞态条件；false：不存在竞态条件
   */
  private static boolean isPathRacing(List<Tuple2<FlowNode, Integer>> path1, List<Tuple2<FlowNode, Integer>> path2) {
    for (var tuple1 : path1) {
      var node1 = tuple1._1();
      var port1 = tuple1._2();
      for (var tuple2 : path2) {
        var node2 = tuple2._1();
        var port2 = tuple2._2();
        if (node1 != node2) {
          continue;
        }
        // 如果两个路径连接到同一个节点的不同端口，则存在竞态条件
        if (!Objects.equals(port1, port2)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * 获取节点+输出端口的所有下游路径
   *
   * @param nodePort 节点 + 输出端口
   * @return 所有下游路径列表
   */
  private static List<List<Tuple2<FlowNode, Integer>>> getDownstreamNodesPaths(Tuple2<FlowNode, Integer> nodePort) {
    final var node = nodePort._1();
    final var port = nodePort._2();
    final var outgoingConnections = node.getOutgoingConnections().stream()
        .filter(LogicFlowUtil::isActiveConnection)
        .filter(connection -> Objects.equals(connection.getSourceOutputPort(), port))
        .collect(Collectors.toList());

    List<List<Tuple2<FlowNode, Integer>>> result = new ArrayList<>();
    if (outgoingConnections.isEmpty()) {
      result.add(new ArrayList<>());
      return result;
    }

    // 对每个下游路径执行递归调用
    for (var connection : outgoingConnections) {
      var targetNode = connection.getTargetNode();
      var targetPort = connection.getTargetInputPort();
      // TODO: 先检查各个连线是否有效
      if (targetNode == null) {
        continue;
      }
      var targetNodeOutputCount = targetNode.getOutputPorts().size();
      // 目标节点没有输出端口时依然合法（例如终点节点、纯消费节点）；仍需纳入路径参与竞态检查。
      if (targetNodeOutputCount == 0) {
        var path = new ArrayList<Tuple2<FlowNode, Integer>>();
        path.add(Tuple.of(targetNode, targetPort));
        result.add(path);
        continue;
      }
      var paths = IntStream.range(0, targetNodeOutputCount)
          .mapToObj(i -> getDownstreamNodesPaths(Tuple.of(targetNode, i)).stream()
              .peek(path -> path.add(0, Tuple.of(targetNode, targetPort)))
              .collect(Collectors.toList()))
          .flatMap(List::stream)
          .collect(Collectors.toList());
      result.addAll(paths);
    }
    return result;
  }

  /**
   * 检查输入输出类型兼容性
   *
   * @param model 逻辑流模型
   * @return true：输入输出类型兼容；false：输入输出类型不兼容
   */
  public static boolean validateInputTypeCompatibility(FlowModel model) {
    var nodes = model.getNodes().stream()
        .filter(FlowNode::isEnabled)
        .collect(Collectors.toList());

    // 检查输入输出
    final var exitNode = model.getExitNode();
    final var flowInputTypes = model.getInputDataType();
    final var flowOutputTypes = model.getOutputDataType();
    if (getMainFlowNodes(model).stream()
        .filter(node -> node.getIncomingConnections().stream().noneMatch(LogicFlowUtil::isActiveConnection))
        .flatMap(node -> node.getInputPorts().stream())
        .anyMatch(port -> !flowInputTypes.isCompatible(port.getDataType()))) {
      return false;
    }
    if (getErrorFlowNodes(model).stream()
        .filter(node -> node.getIncomingConnections().stream().noneMatch(LogicFlowUtil::isActiveConnection))
        .anyMatch(node -> !validateErrorInputCompatibility(node))) {
      return false;
    }
    if (!validateSuccessfulTerminalOutputCompatibility(model, getMainFlowNodes(model), exitNode, flowOutputTypes)) {
      return false;
    }
    if (!validateSuccessfulTerminalOutputCompatibility(model, getErrorFlowNodes(model), null, flowOutputTypes)) {
      return false;
    }

    // 检查节点间连接
    for (var node : nodes) {
      var inputPorts = node.getInputPorts();
      for (var i = 0; i < inputPorts.size(); i++) {
        var inputPort = inputPorts.get(i);
        int finalI = i;
        var dataType = inputPort.getDataType();

        var connections = node.getIncomingConnections().stream()
            .filter(LogicFlowUtil::isWellFormedActiveConnection)
            .filter(conn -> conn.getTargetInputPort() == finalI)
            .collect(Collectors.toList());
        for (var connection : connections) {
          var sourceNode = connection.getSourceNode();
          var sourcePort = connection.getSourceOutputPort();
          // 防止因为数据错误、节点输出端口错误导致报异常
          if (sourceNode == null || sourcePort >= sourceNode.getOutputPorts().size()) {
            return false;
          }
          var sourcePortType = sourceNode.getOutputPorts().get(sourcePort).getDataType();
          if (!dataType.isCompatible(sourcePortType)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean validateErrorInputCompatibility(FlowNode node) {
    if (!isActiveNode(node)) {
      return true;
    }

    var inputPorts = node.getInputPorts();
    if (inputPorts.size() > 1) {
      return false;
    }
    return inputPorts.stream()
        .allMatch(port -> DataTypeEnum.MAP.isCompatible(port.getDataType()));
  }

  private static boolean validateSuccessfulTerminalOutputCompatibility(FlowModel model,
                                                                       Set<FlowNode> scopeNodes,
                                                                       FlowNode explicitExitNode,
                                                                       DataTypeEnum flowOutputTypes) {
    return getSuccessfulTerminalNodes(model, scopeNodes, explicitExitNode).stream()
        .allMatch(node -> isSuccessfulTerminalOutputCompatible(node, flowOutputTypes));
  }

  private static boolean isSuccessfulTerminalOutputCompatible(FlowNode node, DataTypeEnum flowOutputTypes) {
    if (node instanceof AbstractTerminalNode) {
      return !node.getInputPorts().isEmpty()
          && flowOutputTypes.isCompatible(node.getInputPorts().get(0).getDataType());
    }
    return !node.getOutputPorts().isEmpty()
        && flowOutputTypes.isCompatible(node.getOutputPorts().get(0).getDataType());
  }

  private static boolean isActiveNode(FlowNode node) {
    return node != null && node.isEnabled();
  }

  private static boolean isActiveConnection(FlowConnection connection) {
    return connection != null
        && isActiveNode(connection.getSourceNode())
        && isActiveNode(connection.getTargetNode());
  }

  /**
   * 活跃且连线端口有效（不越界）的连接。
   * <p>
   * 这里的“端口有效”以节点实例的端口配置为准（允许节点基于规则补齐端口），同时仍受节点的 maxInputPortCount/maxOutputPortCount 限制。
   */
  private static boolean isWellFormedActiveConnection(FlowConnection connection) {
    if (!isActiveConnection(connection)) {
      return false;
    }
    var sourcePort = connection.getSourceOutputPort();
    var targetPort = connection.getTargetInputPort();
    if (sourcePort == null || targetPort == null) {
      return false;
    }
    if (sourcePort < 0 || targetPort < 0) {
      return false;
    }
    var sourceNode = connection.getSourceNode();
    var targetNode = connection.getTargetNode();
    return sourcePort < sourceNode.getOutputPorts().size()
        && targetPort < targetNode.getInputPorts().size();
  }
}
