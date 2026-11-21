package com.ouroboros.logicflow.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.vavr.control.Try;

import com.ouroboros.logicflow.compiler.FunctionalFlowCompiler;
import com.ouroboros.logicflow.exception.*;
import com.ouroboros.logicflow.nodes.NodeFactory;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.util.LogicFlowUtil;
import com.ouroboros.logicflow.util.LogicFlowJson;

public class DefaultFlowModel implements FlowModel {
  final List<FlowNode> nodes = new ArrayList<>();
  final List<FlowConnection> connections = new ArrayList<>();
  final private FlowModelMeta meta;
  private String revision;
  FlowNode entryNode;
  FlowNode exitNode;
  FlowNode errorEntryNode;
  Function<Object[], Try<Object>> executeFunction;
  List<DesignException> errors = new ArrayList<>();

  DefaultFlowModel(FlowModelMeta meta) {
    this.meta = meta;
  }

  private static String computeRevision(FlowModelMeta meta) {
    try {
      Map<String, Object> definitionSnapshot = new LinkedHashMap<>();
      definitionSnapshot.put("name", meta.getName());
      definitionSnapshot.put("description", meta.getDescription());
      definitionSnapshot.put("entryNode", meta.getEntryNode());
      definitionSnapshot.put("exitNode", meta.getExitNode());
      definitionSnapshot.put("errorEntryNode", meta.getErrorEntryNode());
      definitionSnapshot.put("inputDataType", meta.getInputDataType());
      definitionSnapshot.put("outputDataType", meta.getOutputDataType());
      definitionSnapshot.put("enabled", meta.isEnabled());
      definitionSnapshot.put("nodes", meta.getNodes());
      definitionSnapshot.put("connections", meta.getConnections());
      byte[] definition = LogicFlowJson.OBJECT_MAPPER.writeValueAsBytes(definitionSnapshot);
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(definition);
      return java.util.HexFormat.of().formatHex(digest);
    } catch (java.io.IOException | NoSuchAlgorithmException error) {
      throw new IllegalStateException("逻辑流发布 revision 无法计算", error);
    }
  }

  void initialize(NodeReferenceCatalog references) {
    revision = computeRevision(meta);
    // 先构造所有的连接
    meta.getConnections().forEach(connectionMeta -> {
      var connection = new DefaultFlowConnection(this, connectionMeta);
      connections.add(connection);
    });

    // 构造所有节点
    meta.getNodes().forEach(nodeMeta -> {
      try {
        var node = NodeFactory.createNode(this, nodeMeta, references);
        nodes.add(node);
      }
      catch (DesignException e) {
        addError(e);
      }
      catch (Throwable e) {
        addError(new NodeDesignException("节点构造错误", nodeMeta));
      }
    });

    // 把节点实例和连接实例关联起来
    // 这里不在 Connection 类的 getter 里通过对本模型的引用来找源节点和目标节点
    // 一是为了避免循环依赖，二是为了避免 Connection 类的 getter 里每次都要遍历节点列表
    connections.forEach(connection -> {
      if (!(connection instanceof DefaultFlowConnection conn)) {
        return;
      }
      var sourceNode = getNode(conn.getSourceName());
      var targetNode = getNode(conn.getTargetName());
      if (sourceNode == null || targetNode == null) {
        var connDesc = "连线：" + conn.getSourceName() + ":" + conn.getSourceOutputPort() + " -> " + conn.getTargetName() + ":" + conn.getTargetInputPort();
        StringBuilder sb = new StringBuilder();
        if (sourceNode == null) {
          sb.append("源节点不存在：").append(conn.getSourceName()).append(" ");
        }
        if (targetNode == null) {
          sb.append("目标节点不存在：").append(conn.getTargetName()).append(" ");
        }
        addError(new NodeNotFoundException(connDesc + " 存在错误，" + sb, meta));
      }
      conn.setSourceNode(sourceNode);
      conn.setTargetNode(targetNode);
    });

    // 校验连线端口是否越界：不允许连接到节点的 maxInputPortCount/maxOutputPortCount 之外。
    // 注意：输入端口数量可能会基于连线做补齐，但仍会被 maxInputPortCount 限制。
    var invalidConnections = connections.stream()
        .filter(conn -> conn != null && conn.getSourceNode() != null && conn.getTargetNode() != null)
        .filter(conn -> conn.getSourceNode().isEnabled() && conn.getTargetNode().isEnabled())
        .filter(conn -> {
          var sourcePort = conn.getSourceOutputPort();
          var targetPort = conn.getTargetInputPort();
          return sourcePort < 0
              || targetPort < 0
              || sourcePort >= conn.getSourceNode().getOutputPorts().size()
              || targetPort >= conn.getTargetNode().getInputPorts().size();
        })
        .map(conn -> FlowConnectionMeta.builder()
            .sourceNode(conn.getSourceNode().getName())
            .sourceOutputPort(conn.getSourceOutputPort())
            .targetNode(conn.getTargetNode().getName())
            .targetInputPort(conn.getTargetInputPort())
            .build())
        .collect(Collectors.toList());
    if (!invalidConnections.isEmpty()) {
      var invalidConnectionDescriptions = invalidConnections.stream()
          .map(conn -> String.format("%s:%d -> %s:%d",
              conn.getSourceNode(),
              conn.getSourceOutputPort(),
              conn.getTargetNode(),
              conn.getTargetInputPort()))
          .collect(Collectors.toList());
      addError(new InvalidConnectionDetectedException(meta, invalidConnectionDescriptions, invalidConnections));
    }

    // 检查所有的节点，如果已激活但验证失败，则添加错误
    var nodeErrors = nodes.stream()
        .filter(node -> node.isEnabled() && !node.isValid())
        .flatMap(node -> node.getErrors().stream())
        .collect(Collectors.toList());
    if (!nodeErrors.isEmpty()) {
      addError(new ProblematicNodesDetectedException(meta, nodeErrors));
    }

    // 找到入口节点和出口节点
    entryNode = getNode(meta.getEntryNode());
    exitNode = getNode(meta.getExitNode());
    errorEntryNode = getNode(meta.getErrorEntryNode());

    if (entryNode == null) {
      addError(new EntryNotFoundException(meta));
    }
    if (meta.getExitNode() != null && exitNode == null) {
      addError(new ExitNotFoundException(meta));
    }
    if (meta.getErrorEntryNode() != null && errorEntryNode == null) {
      addError(new ErrorEntryNotFoundException(meta));
    }
    var mainScope = LogicFlowUtil.getMainFlowNodes(this);
    if (!LogicFlowUtil.hasSuccessfulTerminal(this, mainScope, getExitNode())) {
      addError(new MissingSuccessfulTerminalException(meta));
    }

    // 验证流程
    Try.of(() -> LogicFlowUtil.topologicalSort(this)).toEither()
        .peekLeft(e -> addError(new CyclicDetectedException(meta)))
        .peek((nodes) -> {
          LogicFlowUtil.validateFlowScopeIssue(this)
              .ifPresent(issue -> addError(new InvalidFlowScopeDetectedException(
                  issue.getMessage(),
                  meta,
                  issue.getNodeNames()
              )));
          var errorScope = LogicFlowUtil.getErrorFlowNodes(this);

          var invalidMainScopeLeaves = LogicFlowUtil.getInvalidLeafNodes(mainScope, getExitNode());
          if (!invalidMainScopeLeaves.isEmpty()) {
            addError(new InvalidLeafNodeDetectedException(meta,
                invalidMainScopeLeaves.stream().map(FlowNode::getName).collect(Collectors.toList())));
          }

          if (!errorScope.isEmpty()) {
            var invalidErrorScopeLeaves = LogicFlowUtil.getInvalidLeafNodes(errorScope, null);
            if (!invalidErrorScopeLeaves.isEmpty()) {
              addError(new InvalidLeafNodeDetectedException(meta,
                  invalidErrorScopeLeaves.stream().map(FlowNode::getName).collect(Collectors.toList())));
            }
          }

          if (getErrorEntryNode() == null && getEntryNode() != null) {
            var mainScopeNodes = LogicFlowUtil.getMainFlowNodes(this);
            var unreachableNodes = LogicFlowUtil.getUnreachableEnabledNodes(this, mainScopeNodes);
            if (!unreachableNodes.isEmpty()) {
              addError(new UnreachableNodeDetectedException(meta,
                  unreachableNodes.stream().map(FlowNode::getName).collect(Collectors.toList())));
            }
          }

          if (!LogicFlowUtil.checkSuccessfulTerminalExclusivity(this, mainScope, getExitNode())) {
            addError(new SuccessfulTerminalConflictDetectedException(meta));
          }
          if (!errorScope.isEmpty() && !LogicFlowUtil.checkSuccessfulTerminalExclusivity(this, errorScope, null)) {
            addError(new SuccessfulTerminalConflictDetectedException(meta));
          }
          // 验证所有节点的输入端口是否都连接了
          if (!LogicFlowUtil.checkAllInputPortsConnected(this)) {
            addError(new UnconnectedInputPortException(meta));
          }
          // 验证是否存在竞争条件
          if (!LogicFlowUtil.checkRacingCondition(this)) {
            addError(new RacingConditionDetectedException(meta));
          }
          // 验证是否存在输入端口冲突
          if (!LogicFlowUtil.checkInputPortConflicts(this)) {
            addError(new InputPortConflictDetectedException(meta));
          }
          // 检查输入输出数据类型是否匹配
          if (!LogicFlowUtil.validateInputTypeCompatibility(this)) {
            addError(new InputOutputDataTypeMismatchedException(meta));
          }
        });

    if (!isEnabled()) {
      executeFunction = (inputs) -> Try.failure(new FlowDesignException("流程未启用", meta));
      return;
    }

    if (!isValid()) {
      executeFunction = (inputs) -> Try.failure(new FlowDesignException("流程存在错误: " + errors.stream().map(Exception::getMessage).collect(Collectors.joining(",")), meta));
      return;
    }

    // 构造执行函数
    executeFunction = FunctionalFlowCompiler.INSTANCE.compile(this);
  }

  @Override
  public List<DesignException> getErrors() {
    return errors;
  }

  protected void addError(DesignException error) {
    errors.add(error);
  }

  @Override
  public Boolean isValid() {
    return errors.size() == 0;
  }

  @Override
  public Boolean isEnabled() {
    return meta.isEnabled();
  }

  void rejectReferenceCycle() {
    addError(new FlowDesignException("流程引用存在环路: " + getName(), meta));
    executeFunction = inputs -> Try.failure(new FlowDesignException("流程引用存在环路", meta));
  }

  @Override
  public Try<Object> execute(Object... inputs) {
    return executeFunction.apply(inputs);
  }

  @Override
  public String getName() {
    return meta.getName();
  }

  @Override
  public String getRevision() {
    if (revision == null) {
      throw new IllegalStateException("逻辑流尚未初始化，无法获取 revision");
    }
    return revision;
  }

  @Override
  public String getDescription() {
    return meta.getDescription();
  }

  @Override
  public List<FlowNode> getNodes() {
    return nodes;
  }

  @Override
  public List<FlowConnection> getConnections() {
    return connections;
  }

  @Override
  public FlowNode getEntryNode() {
    return entryNode;
  }

  @Override
  public FlowNode getExitNode() {
    return exitNode;
  }

  @Override
  public FlowNode getErrorEntryNode() {
    return errorEntryNode;
  }

  @Override
  public DataTypeEnum getInputDataType() {
    return meta.getInputDataTypeEnum();
  }

  @Override
  public DataTypeEnum getOutputDataType() {
    return meta.getOutputDataTypeEnum();
  }

  @Override
  public FlowNode getNode(String name) {
    if (name == null) {
      return null;
    }
    return nodes.stream().filter(node -> name.equals(node.getName())).findFirst().orElse(null);
  }
}
