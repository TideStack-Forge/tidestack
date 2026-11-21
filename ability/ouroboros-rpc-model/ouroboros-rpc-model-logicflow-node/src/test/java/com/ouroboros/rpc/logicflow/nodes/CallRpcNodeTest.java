package com.ouroboros.rpc.logicflow.nodes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vavr.control.Try;

import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.DataTypeEnum;
import com.ouroboros.logicflow.model.FlowConnection;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.rpc.exception.RPCException;
import com.ouroboros.rpc.execution.RpcInvocationService;
import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.execution.RpcInvocationResult;

class CallRpcNodeTest {

  @Test
  void node_throwsWhenModelNameMissing() {
    NodeDesignException exception = assertThrows(
        NodeDesignException.class,
        () -> new CallRpcNode.Node(flowModel(), nodeMeta(""))
    );

    assertEquals("未配置RPC模型", exception.getMessage());
  }

  @Test
  void execute_routesCallsThroughExecutor() {
    RpcInvocationService executor = mock(RpcInvocationService.class);
    Map<String, Object> input = Collections.singletonMap("userId", "42");
    Map<String, Object> payload = Collections.singletonMap("status", "ok");
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.success(payload, 16L));
    new CallRpcNode(executor);

    CallRpcNode.Node node = new CallRpcNode.Node(flowModel(), nodeMeta("demoRpc"));

    Try<List<Object>> result = node.execute(Collections.singletonList(input));

    assertTrue(result.isSuccess());
    assertEquals(Collections.singletonList(payload), result.get());
    ArgumentCaptor<RpcInvocationRequest> captor = ArgumentCaptor.forClass(RpcInvocationRequest.class);
    verify(executor).invoke(captor.capture());
    RpcInvocationRequest request = captor.getValue();
    assertEquals("demoRpc", request.getModelName());
    assertEquals(input, request.getParameters());
    assertFalse(request.isInternalCall());
  }

  @Test
  void execute_rejectsWrongInputCount() {
    CallRpcNode.Node node = new CallRpcNode.Node(flowModel(), nodeMeta("demoRpc"));

    Try<List<Object>> result = node.execute(Collections.emptyList());

    assertTrue(result.isFailure());
    assertTrue(result.getCause() instanceof FlowRuntimeException);
    assertEquals("RPC节点只能接受一个输入", result.getCause().getMessage());
  }

  @Test
  void execute_rejectsNonMapInput() {
    CallRpcNode.Node node = new CallRpcNode.Node(flowModel(), nodeMeta("demoRpc"));

    Try<List<Object>> result = node.execute(Collections.singletonList("invalid"));

    assertTrue(result.isFailure());
    assertTrue(result.getCause() instanceof FlowRuntimeException);
    assertEquals("RPC节点输入数据类型不匹配", result.getCause().getMessage());
  }

  @Test
  void execute_mapsExecutorFailuresToFlowRuntimeException() {
    RpcInvocationService executor = mock(RpcInvocationService.class);
    when(executor.invoke(any(RpcInvocationRequest.class)))
        .thenReturn(RpcInvocationResult.failure(new RPCException("boom")));
    new CallRpcNode(executor);

    CallRpcNode.Node node = new CallRpcNode.Node(flowModel(), nodeMeta("demoRpc"));

    Try<List<Object>> result = node.execute(Collections.singletonList(Collections.singletonMap("k", "v")));

    assertTrue(result.isFailure());
    assertTrue(result.getCause() instanceof FlowRuntimeException);
    assertEquals("RPC调用失败！", result.getCause().getMessage());
    assertTrue(result.getCause().getCause() instanceof RPCException);
  }

  private FlowNodeMeta nodeMeta(String modelName) {
    return FlowNodeMeta.builder()
        .name("callRpc")
        .type("CallRPC")
        .extraProp("modelName", modelName)
        .build();
  }

  private FlowModel flowModel() {
    return new FlowModel() {
      @Override
      public String getName() {
        return "testFlow";
      }

      @Override
      public String getDescription() {
        return "test";
      }

      @Override
      public List<FlowNode> getNodes() {
        return Collections.emptyList();
      }

      @Override
      public List<FlowConnection> getConnections() {
        return Collections.emptyList();
      }

      @Override
      public Boolean isEnabled() {
        return true;
      }

      @Override
      public Boolean isValid() {
        return true;
      }

      @Override
      public DataTypeEnum getInputDataType() {
        return DataTypeEnum.ANY;
      }

      @Override
      public DataTypeEnum getOutputDataType() {
        return DataTypeEnum.ANY;
      }

      @Override
      public Try<Object> execute(Object... inputs) {
        return Try.success(null);
      }

      @Override
      public FlowNode getEntryNode() {
        return null;
      }

      @Override
      public FlowNode getExitNode() {
        return null;
      }

      @Override
      public FlowNode getNode(String name) {
        return null;
      }

      @Override
      public List<com.ouroboros.logicflow.exception.DesignException> getErrors() {
        return Collections.emptyList();
      }
    };
  }
}
