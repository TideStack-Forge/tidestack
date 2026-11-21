package com.ouroboros.devhelper.metadata;

import com.ouroboros.devhelper.command.CommandFailureType;

public final class MetadataSnapshotRejectedEvent {
  private final String appName;
  private final Long runtimeGeneration;
  private final String helperInstanceId;
  private final long helperStartedAtEpochMillis;
  private final String appIncarnationId;
  private final String refreshOperationId;
  private final String reason;
  private final CommandFailureType failureType;
  private final HelperRuntimeTarget runtimeTarget;

  public MetadataSnapshotRejectedEvent(
      String appName,
      Long runtimeGeneration,
      String helperInstanceId,
      long helperStartedAtEpochMillis,
      String appIncarnationId,
      String refreshOperationId,
      String reason,
      HelperRuntimeTarget runtimeTarget
  ) {
    this(
        appName,
        runtimeGeneration,
        helperInstanceId,
        helperStartedAtEpochMillis,
        appIncarnationId,
        refreshOperationId,
        reason,
        null,
        runtimeTarget
    );
  }

  public MetadataSnapshotRejectedEvent(
      String appName,
      Long runtimeGeneration,
      String helperInstanceId,
      long helperStartedAtEpochMillis,
      String appIncarnationId,
      String refreshOperationId,
      String reason,
      CommandFailureType failureType,
      HelperRuntimeTarget runtimeTarget
  ) {
    this.appName = appName;
    this.runtimeGeneration = runtimeGeneration;
    this.helperInstanceId = helperInstanceId;
    this.helperStartedAtEpochMillis = helperStartedAtEpochMillis;
    this.appIncarnationId = appIncarnationId;
    this.refreshOperationId = refreshOperationId;
    this.reason = reason;
    this.failureType = failureType;
    this.runtimeTarget = runtimeTarget;
  }

  public String getAppName() {
    return appName;
  }

  public Long getRuntimeGeneration() {
    return runtimeGeneration;
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

  public String getRefreshOperationId() {
    return refreshOperationId;
  }

  public String getReason() {
    return reason;
  }

  public CommandFailureType getFailureType() {
    return failureType;
  }

  public HelperRuntimeTarget getRuntimeTarget() {
    return runtimeTarget;
  }
}
