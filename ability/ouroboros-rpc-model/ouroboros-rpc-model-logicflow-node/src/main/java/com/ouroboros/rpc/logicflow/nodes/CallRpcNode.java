package com.ouroboros.rpc.logicflow.nodes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.AbstractNode;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeBuilder;
import com.ouroboros.rpc.execution.RpcInvocationService;
import com.ouroboros.rpc.execution.RpcInvocationRequest;

/**
 * @author liansz
 **/
@Component
public class CallRpcNode {
  private static RpcInvocationService rpcInvocationExecutor;

  public CallRpcNode(RpcInvocationService rpcInvocationExecutor) {
    CallRpcNode.rpcInvocationExecutor = rpcInvocationExecutor;
  }

  public static class Node extends AbstractNode<Object> {
    private final String modelName;

    protected Node(FlowModel flowModel, FlowNodeMeta meta) {
      super(flowModel, meta);
      modelName = meta.getExtraProp(String.class, "modelName")
          .map(String::trim)
          .orElse("");
      if (modelName.isEmpty()) {
        throw new NodeDesignException("未配置RPC模型", meta);
      }
    }

    @Override
    protected List<Object> doExecute(List<Object> inputs) {
      if (inputs.size() != 1) {
        throw new FlowRuntimeException("RPC节点只能接受一个输入", this, inputs);
      }

      final var input = inputs.get(0);
      if (!(input instanceof Map<?, ?>)) {
        throw new FlowRuntimeException("RPC节点输入数据类型不匹配", this, inputs);
      }

      if (rpcInvocationExecutor == null) {
        throw new FlowRuntimeException("RPC调用失败！", new IllegalStateException("RPC调用执行器未初始化"), this, inputs);
      }

      final var parameters = (Map<String, Object>) input;
      final var result = rpcInvocationExecutor.invoke(new RpcInvocationRequest(
          modelName,
          parameters,
          false,
          "logicflow-node",
          Collections.emptyMap()
      ));
      if (result == null) {
        throw new FlowRuntimeException("RPC调用失败！", new IllegalStateException("RPC调用未返回结果"), this, inputs);
      }
      if (result.isFailure()) {
        throw new FlowRuntimeException("RPC调用失败！", result.getError(), this, inputs);
      }
      return Collections.singletonList(result.getPayload());
    }

    @Override
    protected Integer maxInputPortCount() {
      return 1;
    }
  }

  public static class Builder implements NodeBuilder<CallRpcNode.Node> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "CallRPC".equalsIgnoreCase(meta.getType());
    }

    @Override
    public CallRpcNode.Node build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new CallRpcNode.Node(owner, metadata);
    }
  }
}
