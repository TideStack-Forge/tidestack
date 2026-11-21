package com.ouroboros.devhelper.metadata;

public final class MetadataRefreshRequestedEvent {
  private final String appName;
  private final Long runtimeGeneration;
  private final MetadataRefreshProtocol.HelperIdentity helperIdentity;
  private final String refreshOperationId;
  private final HelperRuntimeTarget runtimeTarget;

  public MetadataRefreshRequestedEvent(
      String appName,
      Long runtimeGeneration,
      MetadataRefreshProtocol.HelperIdentity helperIdentity,
      String refreshOperationId,
      HelperRuntimeTarget runtimeTarget
  ) {
    this.appName = appName;
    this.runtimeGeneration = runtimeGeneration;
    this.helperIdentity = helperIdentity;
    this.refreshOperationId = refreshOperationId;
    this.runtimeTarget = runtimeTarget;
  }

  public String getAppName() {
    return appName;
  }

  public Long getRuntimeGeneration() {
    return runtimeGeneration;
  }

  public MetadataRefreshProtocol.HelperIdentity getHelperIdentity() {
    return helperIdentity;
  }

  public String getRefreshOperationId() {
    return refreshOperationId;
  }

  public HelperRuntimeTarget getRuntimeTarget() {
    return runtimeTarget;
  }
}
