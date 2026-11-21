package com.ouroboros.devhelper.metadata;

public final class MetadataSnapshotPublishedEvent {
  private final String appName;
  private final Long runtimeGeneration;
  private final String helperInstanceId;
  private final long helperStartedAtEpochMillis;
  private final String appIncarnationId;
  private final String refreshOperationId;
  private final long refreshRevision;
  private final HelperRuntimeTarget runtimeTarget;

  public MetadataSnapshotPublishedEvent(
      String appName,
      Long runtimeGeneration,
      String helperInstanceId,
      long helperStartedAtEpochMillis,
      String appIncarnationId,
      String refreshOperationId,
      long refreshRevision,
      HelperRuntimeTarget runtimeTarget
  ) {
    this.appName = appName;
    this.runtimeGeneration = runtimeGeneration;
    this.helperInstanceId = helperInstanceId;
    this.helperStartedAtEpochMillis = helperStartedAtEpochMillis;
    this.appIncarnationId = appIncarnationId;
    this.refreshOperationId = refreshOperationId;
    this.refreshRevision = refreshRevision;
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

  public long getRefreshRevision() {
    return refreshRevision;
  }

  public HelperRuntimeTarget getRuntimeTarget() {
    return runtimeTarget;
  }
}
