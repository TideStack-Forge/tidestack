package com.ouroboros.devhelper.metadata;

public final class HelperRuntimeActivatedEvent {
  private final String appName;
  private final MetadataRefreshProtocol.HelperIdentity helperIdentity;
  private final HelperRuntimeTarget runtimeTarget;

  public HelperRuntimeActivatedEvent(
      String appName,
      MetadataRefreshProtocol.HelperIdentity helperIdentity,
      HelperRuntimeTarget runtimeTarget
  ) {
    this.appName = appName;
    this.helperIdentity = helperIdentity;
    this.runtimeTarget = runtimeTarget;
  }

  public String getAppName() {
    return appName;
  }

  public MetadataRefreshProtocol.HelperIdentity getHelperIdentity() {
    return helperIdentity;
  }

  public HelperRuntimeTarget getRuntimeTarget() {
    return runtimeTarget;
  }
}
