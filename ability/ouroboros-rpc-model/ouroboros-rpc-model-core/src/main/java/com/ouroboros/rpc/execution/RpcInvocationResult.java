package com.ouroboros.rpc.execution;

public final class RpcInvocationResult {
  private final Object payload;
  private final Throwable error;
  private final long durationMs;

  private RpcInvocationResult(Object payload, Throwable error, long durationMs) {
    this.payload = payload;
    this.error = error;
    this.durationMs = durationMs;
  }

  public static RpcInvocationResult success(Object payload, long durationMs) {
    return new RpcInvocationResult(payload, null, durationMs);
  }

  public static RpcInvocationResult failure(Throwable error) {
    return failure(error, 0L);
  }

  public static RpcInvocationResult failure(Throwable error, long durationMs) {
    return new RpcInvocationResult(null, error, durationMs);
  }

  public Object getPayload() {
    return payload;
  }

  public Throwable getError() {
    return error;
  }

  public long getDurationMs() {
    return durationMs;
  }

  public boolean isSuccess() {
    return error == null;
  }

  public boolean isFailure() {
    return !isSuccess();
  }
}
