package com.ouroboros.dev.event;

import java.util.EventObject;

public abstract class DevAppEvent extends EventObject {
  private String appName;

  public DevAppEvent(Object source, String appName) {
    super(source);
    this.appName = appName;
  }

  public String getAppName() {
    return appName;
  }
}
