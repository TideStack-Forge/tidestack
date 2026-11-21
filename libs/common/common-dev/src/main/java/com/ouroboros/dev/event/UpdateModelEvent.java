package com.ouroboros.dev.event;

public class UpdateModelEvent extends DevAppModelEvent {
  public UpdateModelEvent(Object source, String appName, Object model) {
    super(source, appName, model);
  }
}
