package com.ouroboros.dev.event;

public class DeleteModelEvent extends DevAppModelEvent {
  public DeleteModelEvent(Object source, String appName, Object model) {
    super(source, appName, model);
  }
}
