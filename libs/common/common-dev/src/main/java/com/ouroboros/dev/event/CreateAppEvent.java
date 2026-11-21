package com.ouroboros.dev.event;

public class CreateAppEvent extends DevAppEvent {
  public CreateAppEvent(Object source, String appName) {
    super(source, appName);
  }
}
