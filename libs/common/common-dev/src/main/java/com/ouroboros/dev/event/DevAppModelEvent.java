package com.ouroboros.dev.event;

public class DevAppModelEvent extends DevAppEvent {
  private final Object model;

  public DevAppModelEvent(Object source, String appName, Object model) {
    super(source, appName);
    this.model = model;
  }

  public Object getModel() {
    return model;
  }
}
