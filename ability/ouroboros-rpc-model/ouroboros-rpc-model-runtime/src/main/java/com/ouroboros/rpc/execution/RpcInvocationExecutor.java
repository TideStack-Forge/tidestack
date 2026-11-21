package com.ouroboros.rpc.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.rpc.exception.RPCException;

public class RpcInvocationExecutor implements RpcInvocationService {
  private final List<RpcProtocolInvoker> protocolInvokers;
  private final List<RpcInvocationInterceptor> interceptors;
  private final List<RpcExecutionResourcesFactory> resourceFactories;

  public RpcInvocationExecutor(List<RpcProtocolInvoker> protocolInvokers, List<RpcInvocationInterceptor> interceptors) {
    this(protocolInvokers, interceptors, List.of());
  }

  public RpcInvocationExecutor(List<RpcProtocolInvoker> protocolInvokers, List<RpcInvocationInterceptor> interceptors,
      List<RpcExecutionResourcesFactory> resourceFactories) {
    this.resourceFactories = List.copyOf(resourceFactories);
    this.protocolInvokers = protocolInvokers == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(protocolInvokers));
    this.interceptors = interceptors == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(interceptors));
  }

  public RpcInvocationResult invoke(RpcInvocationRequest request) {
    return invoke(request, null);
  }

  public RpcInvocationResult invoke(RpcInvocationRequest request, RpcExecutionResources resources) {
    try {
      for (var factory : resourceFactories) {
        resources = factory.create(request, resources);
      }
    } catch (RuntimeException exception) {
      return RpcInvocationResult.failure(new RPCException("准备调用资源失败", exception));
    }
    Optional<RPCModelDefinition> definitionOptional = resources == null
        ? RPCModelDefinitionCenter.getDefinition(request.getModelName()) : resources.findDefinition(request.getModelName());
    if (!definitionOptional.isPresent()) {
      return RpcInvocationResult.failure(new RPCException("RPC模型不存在: " + request.getModelName()));
    }

    RPCModelDefinition definition = definitionOptional.get();
    Optional<RpcProtocolInvoker> invokerOptional = protocolInvokers.stream()
        .filter(invoker -> invoker.supports(definition))
        .findFirst();
    if (!invokerOptional.isPresent()) {
      return RpcInvocationResult.failure(new RPCException("未匹配到协议执行器: " + definition.getType()));
    }

    RpcInvocationContext context = new RpcInvocationContext(request, definition, resources);
    try {
      RpcInvocationResult result = buildChain(invokerOptional.get(), 0).proceed(context);
      if (result != null && result.isSuccess() && resources != null) {
        result = RpcInvocationResult.success(resources.redact(result.getPayload()), result.getDurationMs());
      }
      context.setResult(result);
      if (result != null) {
        context.setProtocolResponse(result.getPayload());
        if (result.isFailure()) {
          context.setError(result.getError());
        }
      }
      return result;
    } catch (Exception exception) {
      context.setError(exception);
      RpcInvocationResult result = RpcInvocationResult.failure(new RPCException("调用失败", exception));
      context.setResult(result);
      return result;
    }
  }

  private RpcInvocationInterceptor.Chain buildChain(RpcProtocolInvoker protocolInvoker, int index) {
    if (index >= interceptors.size()) {
      return context -> {
        RpcInvocationResult result = protocolInvoker.invoke(context);
        context.setResult(result);
        if (result != null) {
          context.setProtocolResponse(result.getPayload());
          if (result.isFailure()) {
            context.setError(result.getError());
          }
        }
        return result;
      };
    }

    return context -> interceptors.get(index).intercept(context, buildChain(protocolInvoker, index + 1));
  }
}
