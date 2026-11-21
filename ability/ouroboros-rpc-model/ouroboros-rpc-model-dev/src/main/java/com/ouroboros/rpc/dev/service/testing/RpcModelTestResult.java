package com.ouroboros.rpc.dev.service.testing;

import java.util.List;
import java.util.Map;

/** Public output contains sanitized evidence only, never credentials or invocation payload objects. */
public record RpcModelTestResult(String testId, String revision, boolean success, long durationMs, int attemptCount,
                                  Request request, Response response, Error error,
                                  List<Attempt> attempts, List<Attempt> authenticationCalls) {
  public record Error(String stage, String reason, String message, String resource, String field, boolean retryable) { }
  public record Body(String text, String contentType, boolean truncated, Integer byteLength, String omittedReason) { }
  public record Request(String method, String url, Map<String, List<String>> headers, Map<String, String> cookies,
                        Body body, boolean complete) { }
  public record Response(int status, Map<String, List<String>> headers, Body body) { }
  public record Attempt(String modelName, int index, long durationMs, Request request, Response response, Error error) { }
}
