package com.ouroboros.dev.runtime.service;

import java.time.Instant;

public class RuntimeOperationState {
  public enum Type {
    RESTART,
    RELOAD_WORKSPACE
  }

  private final Type type;
  private final Long runtimeGeneration;
  private final Instant startedAt;

  private RuntimeOperationState(Type type, Long runtimeGeneration, Instant startedAt) {
    this.type = type;
    this.runtimeGeneration = runtimeGeneration;
    this.startedAt = startedAt;
  }

  public static RuntimeOperationState restart(long runtimeGeneration) {
    return new RuntimeOperationState(Type.RESTART, runtimeGeneration, Instant.now());
  }

  public static RuntimeOperationState reloadWorkspace(long runtimeGeneration) {
    return new RuntimeOperationState(Type.RELOAD_WORKSPACE, runtimeGeneration, Instant.now());
  }

  public Type getType() {
    return type;
  }

  public Long getRuntimeGeneration() {
    return runtimeGeneration;
  }

  public Instant getStartedAt() {
    return startedAt;
  }
}
