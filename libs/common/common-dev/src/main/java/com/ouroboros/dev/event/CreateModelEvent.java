package com.ouroboros.dev.event;

public class CreateModelEvent extends DevAppModelEvent {
  public CreateModelEvent(Object source, String appName, Object model) {
    super(source, appName, model);
  }
}
