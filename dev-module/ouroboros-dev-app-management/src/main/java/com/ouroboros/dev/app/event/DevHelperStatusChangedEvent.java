package com.ouroboros.dev.app.event;

public class DevHelperStatusChangedEvent {
  private final String appName;

  public DevHelperStatusChangedEvent(String appName) {
    this.appName = appName;
  }

  public String getAppName() {
    return appName;
  }
}
