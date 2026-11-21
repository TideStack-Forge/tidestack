package com.ouroboros.rpc.security;

import java.util.Map;

import io.vavr.control.Try;

import com.ouroboros.rpc.RPCRequest;

/**
 * @author liansz
 **/
public interface RPCAuthCredential<T extends RPCRequest> {

  /**
   * 名称
   *
   * @return
   */
  String getName();

  /**
   * 标题
   *
   * @return
   */
  String getTitle();

  /**
   * 获取接口类型，在接口中使用。
   *
   * @return
   */
  String getType();

  /**
   * 授权
   *
   * @param request
   * @return
   */
  Try<T> applyToRequest(T request, Map<String, Object> context);

  /** Invocation dependencies are supplied by the executor, never by business parameters. */
  default Try<T> applyToRequest(T request, Map<String, Object> context,
      com.ouroboros.rpc.execution.RpcExecutionResources resources) {
    return applyToRequest(request, context);
  }
}
