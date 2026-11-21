package com.ouroboros.dev.event;

public class DeleteAppEvent extends DevAppEvent {
  public DeleteAppEvent(Object source, String appName) {
    super(source, appName);
  }
}
