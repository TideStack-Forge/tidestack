package com.ouroboros.rpc.execution;

import java.util.List;
import java.util.Map;

/** Internal transport evidence. Consumers must redact it before exposing or logging it. */
public record RpcHttpExchange(String modelName, String purpose, int attempt, String method, String url,
                              Map<String, List<String>> requestHeaders, byte[] requestBody,
                              Integer status, Map<String, List<String>> responseHeaders, byte[] responseBody,
                              long durationMs, String errorStage) {
  public RpcHttpExchange {
    requestHeaders = copyHeaders(requestHeaders);
    responseHeaders = copyHeaders(responseHeaders);
    requestBody = requestBody == null ? null : requestBody.clone();
    responseBody = responseBody == null ? null : responseBody.clone();
  }

  private static Map<String, List<String>> copyHeaders(Map<String, List<String>> headers) {
    var copy = new java.util.LinkedHashMap<String, List<String>>();
    headers.forEach((key, values) -> copy.put(key, List.copyOf(values)));
    return java.util.Collections.unmodifiableMap(copy);
  }

  @Override public byte[] requestBody() { return requestBody == null ? null : requestBody.clone(); }
  @Override public byte[] responseBody() { return responseBody == null ? null : responseBody.clone(); }
}
