package com.ouroboros.logicflow.nodes;

import java.util.List;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.exception.NodeTypeNotFoundException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.support.LogicFlowServices;

public final class NodeFactory {
  static List<NodeBuilder<?>> builders = LogicFlowServices.cachedSortedServiceStream(NodeBuilder.class)
      .map(NodeFactory::typedBuilder)
      .collect(Collectors.toList());

  private NodeFactory() {
  }

  private static NodeBuilder<?> typedBuilder(NodeBuilder<?> builder) {
    return builder;
  }

  /** Uses the same builder selection as execution without constructing a node. */
  public static boolean supports(FlowNodeMeta metadata) {
    return builders.stream().filter(builder -> builder.support(metadata)).count() == 1;
  }

  public static FlowNode createNode(
      FlowModel owner,
      FlowNodeMeta nodeMeta,
      NodeReferenceCatalog references
  ) {
    List<NodeBuilder<?>> matchedBuilders = builders.stream()
        .filter(builder -> builder.support(nodeMeta))
        .collect(Collectors.toList());
    if (matchedBuilders.isEmpty()) {
      throw new NodeTypeNotFoundException("未知的节点类型", nodeMeta);
    }
    if (matchedBuilders.size() > 1) {
      throw new NodeDesignException("节点类型存在多个构造器: " + nodeMeta.getType(), nodeMeta, "type");
    }
    var node = matchedBuilders.get(0).build(owner, nodeMeta, references);
    if (node instanceof AbstractNode<?> abstractNode) {
      abstractNode.validateOutputPortMetadata();
    }
    return node;
  }
}
