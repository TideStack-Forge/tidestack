package com.ouroboros.devhelper.metadata;

public final class HelperRuntimeRetiredEvent {
  private final String appName;
  private final String helperInstanceId;
  private final long helperStartedAtEpochMillis;
  private final String appIncarnationId;
  private final HelperRuntimeTarget runtimeTarget;

  public HelperRuntimeRetiredEvent(
      String appName,
      String helperInstanceId,
      long helperStartedAtEpochMillis,
      String appIncarnationId,
      HelperRuntimeTarget runtimeTarget
  ) {
    this.appName = appName;
    this.helperInstanceId = helperInstanceId;
    this.helperStartedAtEpochMillis = helperStartedAtEpochMillis;
    this.appIncarnationId = appIncarnationId;
    this.runtimeTarget = runtimeTarget;
  }

  public String getAppName() {
    return appName;
  }

  public String getHelperInstanceId() {
    return helperInstanceId;
  }

  public long getHelperStartedAtEpochMillis() {
    return helperStartedAtEpochMillis;
  }

  public String getAppIncarnationId() {
    return appIncarnationId;
  }

  public HelperRuntimeTarget getRuntimeTarget() {
    return runtimeTarget;
  }
}
