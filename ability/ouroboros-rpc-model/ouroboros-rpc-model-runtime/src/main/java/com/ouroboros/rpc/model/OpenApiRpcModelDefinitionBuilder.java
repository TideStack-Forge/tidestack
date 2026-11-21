package com.ouroboros.rpc.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.rpc.RPCModelDefinition;
import com.ouroboros.rpc.RPCModelDefinitionBuilder;
import com.ouroboros.rpc.metadata.RPCModelMetadata;

/**
 * OpenAPI RPC 模型静态定义构建器。
 */
public class OpenApiRpcModelDefinitionBuilder implements RPCModelDefinitionBuilder {

  @Override
  public boolean support(RPCModelMetadata metadata) {
    return metadata != null && "OpenAPI".equalsIgnoreCase(metadata.getType());
  }

  @Override
  public RPCModelDefinition build(RPCModelMetadata metadata) {
    return new OpenApiRpcModelDefinition(metadata);
  }

  public static class OpenApiRpcModelDefinition extends RPCModelDefinition.Simple {
    private final Map<String, Object> request;

    public OpenApiRpcModelDefinition(RPCModelMetadata metadata) {
      super(
          metadata.getName(),
          metadata.getTitle(),
          metadata.getGroup(),
          metadata.getType(),
          metadata.getAuthCredential(),
          metadata.getExtraProps()
      );
      this.request = metadata.getRequest() == null
          ? Collections.emptyMap()
          : Collections.unmodifiableMap(new LinkedHashMap<>(metadata.getRequest()));
    }

    public Map<String, Object> getRequest() {
      return request;
    }
  }
}
