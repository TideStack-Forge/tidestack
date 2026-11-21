package com.ouroboros.rpc.dev.service.testing;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

public record RpcModelTestInitRequest(Map<String, String> configValues) {
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static RpcModelTestInitRequest parse(JsonNode json) {
    if (json == null || !json.isObject()) throw new IllegalArgumentException("初始化请求必须是 JSON 对象");
    json.fieldNames().forEachRemaining(name -> {
      if (!"configValues".equals(name)) throw new IllegalArgumentException("初始化请求包含未知字段");
    });
    return new RpcModelTestInitRequest(RpcModelTestRequest.configuration(json));
  }
}
