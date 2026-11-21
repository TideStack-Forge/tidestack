package com.ouroboros.configuration.logicflow.nodes;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractNode;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;

/**
 * @author liansz
 **/
@Component
public class GetConfigNode {

  private static ConfigurationService service;

  public GetConfigNode(ConfigurationService service) {
    GetConfigNode.service = service;
  }

  public static class Node extends AbstractNode<Object> {
    private final String key;

    protected Node(FlowModel flowModel, FlowNodeMeta meta) {
      super(flowModel, meta);
      key = meta.getExtraProp(String.class, "key")
          .map(String::trim)
          .orElse("");
      if (key.isEmpty()) {
        throw new NodeDesignException("键不能为空", meta);
      }
    }

    @Override
    protected List<Object> doExecute(List<Object> inputs) {
      return Collections.singletonList(service.getForExecution(key).orElse(null));
    }

    @Override
    protected Integer maxInputPortCount() {
      return 1;
    }

    @Override
    protected Integer minOutputPortCount() {
      return 0;
    }
  }

  public static class Builder implements NodeBuilder<Node> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "getConfig".equalsIgnoreCase(meta.getType());
    }

    @Override
    public Node build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new Node(owner, metadata);
    }
  }
}
