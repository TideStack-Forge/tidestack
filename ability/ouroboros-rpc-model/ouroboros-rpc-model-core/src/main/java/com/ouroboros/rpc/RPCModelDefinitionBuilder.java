package com.ouroboros.rpc;

import com.ouroboros.rpc.metadata.RPCModelMetadata;

/**
 * RPC 模型定义构建器。
 */
public interface RPCModelDefinitionBuilder {

  /**
   * 是否支持
   *
   * @param metadata 元数据
   * @return 是否支持
   */
  boolean support(RPCModelMetadata metadata);

  /**
   * 构建静态定义
   *
   * @param metadata 元数据
   * @return 定义
   */
  RPCModelDefinition build(RPCModelMetadata metadata);
}
