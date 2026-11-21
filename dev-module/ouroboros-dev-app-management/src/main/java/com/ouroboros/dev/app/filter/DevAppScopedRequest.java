package com.ouroboros.dev.app.filter;

public class DevAppScopedRequest {

  public enum AccessMode {
    VIEW,
    EDIT,
    MANAGE_APP,
    MANAGE_EDIT_ACCESS,
    VIEW_OR_MANAGE_EDIT_ACCESS
  }

  private final String appName;
  private final AccessMode accessMode;

  public DevAppScopedRequest(String appName, AccessMode accessMode) {
    this.appName = appName;
    this.accessMode = accessMode;
  }

  public String getAppName() {
    return appName;
  }

  public AccessMode getAccessMode() {
    return accessMode;
  }
}
